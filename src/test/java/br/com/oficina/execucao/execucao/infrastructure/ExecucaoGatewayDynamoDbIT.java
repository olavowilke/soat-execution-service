package br.com.oficina.execucao.execucao.infrastructure;

import br.com.oficina.execucao.execucao.entities.EtapaExecucao;
import br.com.oficina.execucao.execucao.entities.Execucao;
import br.com.oficina.execucao.execucao.entities.ServicoExecucao;
import br.com.oficina.execucao.execucao.gateways.ExecucaoAlteradaConcorrentementeException;
import br.com.oficina.execucao.execucao.gateways.ExecucaoGateway;
import br.com.oficina.execucao.shared.LocalStackIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class ExecucaoGatewayDynamoDbIT extends LocalStackIT {

    private static final Instant BASE = Instant.parse("2026-01-01T10:00:00Z");

    @Autowired
    private ExecucaoGateway gateway;
    @Autowired
    private DynamoDbClient dynamo;
    @Value("${execucao.dynamodb.tabela}")
    private String tabela;

    @BeforeEach
    void limpaTabela() {
        dynamo.scanPaginator(b -> b.tableName(tabela)).items().forEach(item -> dynamo.deleteItem(b -> b
                .tableName(tabela)
                .key(Map.of("pk", item.get("pk"), "sk", item.get("sk")))));
    }

    private static Execucao nova(Instant criadaEm) {
        return Execucao.nova(UUID.randomUUID(), UUID.randomUUID(),
                List.of(new ServicoExecucao("Troca de oleo", 2)), "obs", criadaEm);
    }

    private static Execucao emTeste(Instant criadaEm) {
        Execucao e = nova(criadaEm);
        e.avancarPara(EtapaExecucao.EM_REPARO, criadaEm.plusSeconds(1));
        e.avancarPara(EtapaExecucao.EM_TESTE, criadaEm.plusSeconds(2));
        return e;
    }

    private static Execucao concluida(Instant criadaEm) {
        Execucao e = emTeste(criadaEm);
        e.avancarPara(EtapaExecucao.CONCLUIDA, criadaEm.plusSeconds(3));
        return e;
    }

    @Test
    void salvarNovaDuasVezesRetornaFalseNaSegunda() {
        Execucao exec = nova(BASE);

        assertThat(gateway.salvarNova(exec)).isTrue();
        assertThat(gateway.salvarNova(exec)).isFalse();
    }

    @Test
    void buscarPorOsIdDevolveExecucaoCompleta() {
        Execucao exec = nova(BASE);
        gateway.salvarNova(exec);

        Execucao lida = gateway.buscarPorOsId(exec.getOsId()).orElseThrow();

        assertThat(lida).usingRecursiveComparison().isEqualTo(exec);
    }

    @Test
    void buscarPorOsIdInexistenteDevolveVazio() {
        assertThat(gateway.buscarPorOsId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void rejeitadaPersisteMotivoESemObservacoes() {
        Execucao exec = Execucao.rejeitada(UUID.randomUUID(), UUID.randomUUID(), "sem capacidade", BASE);
        gateway.salvarNova(exec);

        Execucao lida = gateway.buscarPorOsId(exec.getOsId()).orElseThrow();

        assertThat(lida.getMotivoRejeicao()).isEqualTo("sem capacidade");
        assertThat(lida.getObservacoes()).isNull();
        assertThat(lida.getServicos()).isEmpty();
        assertThat(lida.getEtapa()).isEqualTo(EtapaExecucao.REJEITADA);
    }

    @Test
    void listarAtivasOrdenaPorCriacaoEExcluiTerminais() {
        Execucao maisNova = nova(BASE.plusSeconds(30));
        Execucao maisAntiga = emTeste(BASE);
        Execucao meio = nova(BASE.plusSeconds(10));
        meio.avancarPara(EtapaExecucao.EM_REPARO, BASE.plusSeconds(11));
        Execucao rejeitada = Execucao.rejeitada(UUID.randomUUID(), UUID.randomUUID(), "x", BASE.plusSeconds(1));
        List.of(maisNova, maisAntiga, meio, concluida(BASE.plusSeconds(5)), rejeitada)
                .forEach(gateway::salvarNova);

        List<Execucao> ativas = gateway.listarAtivas();

        assertThat(ativas).extracting(Execucao::getOsId)
                .containsExactly(maisAntiga.getOsId(), meio.getOsId(), maisNova.getOsId());
    }

    @Test
    void contarAtivasIgnoraTerminais() {
        Execucao emReparo = nova(BASE.plusSeconds(1));
        emReparo.avancarPara(EtapaExecucao.EM_REPARO, BASE.plusSeconds(2));
        List.of(nova(BASE), emReparo, emTeste(BASE.plusSeconds(3)), concluida(BASE.plusSeconds(6)))
                .forEach(gateway::salvarNova);

        assertThat(gateway.contarAtivas()).isEqualTo(3);
    }

    @Test
    void contarEListarPaginamQuandoPassamDe1MB() {
        String grande = "x".repeat(100_000);
        for (int i = 0; i < 12; i++) {
            gateway.salvarNova(Execucao.nova(UUID.randomUUID(), UUID.randomUUID(),
                    List.of(new ServicoExecucao("s", 1)), grande, BASE.plusSeconds(i)));
        }

        assertThat(gateway.contarAtivas()).isEqualTo(12);
        assertThat(gateway.listarAtivas()).hasSize(12);
    }

    @Test
    void atualizarItemInexistenteLanca() {
        Execucao exec = nova(BASE);
        exec.avancarPara(EtapaExecucao.EM_REPARO, BASE.plusSeconds(1));

        assertThatThrownBy(() -> gateway.atualizar(exec))
                .isInstanceOf(ExecucaoAlteradaConcorrentementeException.class);
        assertThat(gateway.buscarPorOsId(exec.getOsId())).isEmpty();
    }

    @Test
    void duasAtualizacoesComLeituraVelhaSegundaLancaEPrimeiraSobrevive() {
        Execucao original = nova(BASE);
        gateway.salvarNova(original);
        Execucao leitorA = gateway.buscarPorOsId(original.getOsId()).orElseThrow();
        Execucao leitorB = gateway.buscarPorOsId(original.getOsId()).orElseThrow();
        leitorA.avancarPara(EtapaExecucao.EM_REPARO, BASE.plusSeconds(10));
        leitorB.avancarPara(EtapaExecucao.EM_REPARO, BASE.plusSeconds(20));

        gateway.atualizar(leitorA);

        assertThatThrownBy(() -> gateway.atualizar(leitorB))
                .isInstanceOf(ExecucaoAlteradaConcorrentementeException.class);
        Execucao lida = gateway.buscarPorOsId(original.getOsId()).orElseThrow();
        assertThat(lida.getAtualizadaEm()).isEqualTo(BASE.plusSeconds(10));
        assertThat(lida.getHistorico()).hasSize(2);
    }

    @Test
    void atualizacoesSequenciaisNoMesmoObjetoFuncionam() {
        Execucao exec = nova(BASE);
        gateway.salvarNova(exec);
        exec.avancarPara(EtapaExecucao.EM_REPARO, BASE.plusSeconds(1));
        gateway.atualizar(exec);
        exec.avancarPara(EtapaExecucao.EM_TESTE, BASE.plusSeconds(2));
        gateway.atualizar(exec);

        Execucao lida = gateway.buscarPorOsId(exec.getOsId()).orElseThrow();
        assertThat(lida.getEtapa()).isEqualTo(EtapaExecucao.EM_TESTE);
        assertThat(lida.getVersao()).isEqualTo(2);
    }

    @Test
    void atualizarPersisteHistoricoEMoveIndiceDeEtapa() {
        Execucao exec = nova(BASE);
        gateway.salvarNova(exec);
        exec.avancarPara(EtapaExecucao.EM_REPARO, BASE.plusSeconds(60));

        gateway.atualizar(exec);

        Execucao lida = gateway.buscarPorOsId(exec.getOsId()).orElseThrow();
        assertThat(lida.getEtapa()).isEqualTo(EtapaExecucao.EM_REPARO);
        assertThat(lida.getHistorico()).hasSize(2);
        assertThat(lida.getAtualizadaEm()).isEqualTo(BASE.plusSeconds(60));
        Map<String, AttributeValue> valores = Map.of(":p", AttributeValue.fromS("ETAPA#NA_FILA"));
        assertThat(dynamo.query(b -> b.tableName(tabela).indexName(INDICE_ETAPA)
                .keyConditionExpression("gsi1pk = :p").expressionAttributeValues(valores)).count()).isZero();
        assertThat(gateway.listarAtivas()).extracting(Execucao::getEtapa)
                .containsExactly(EtapaExecucao.EM_REPARO);
    }
}
