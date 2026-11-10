package br.com.oficina.execucao.execucao.entities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExecucaoTest {

    private static final UUID OS = UUID.randomUUID();
    private static final UUID SAGA = UUID.randomUUID();
    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant T1 = T0.plusSeconds(60);
    private static final Instant T2 = T0.plusSeconds(120);
    private static final Instant T3 = T0.plusSeconds(180);
    private static final List<ServicoExecucao> SERVICOS = List.of(new ServicoExecucao("Troca de óleo", 1));

    private Execucao nova() {
        return Execucao.nova(OS, SAGA, SERVICOS, null, T0);
    }

    @Test
    void novaComecaNaFilaComUmHistorico() {
        var e = Execucao.nova(OS, SAGA, SERVICOS, "obs", T0);
        assertThat(e.getEtapa()).isEqualTo(EtapaExecucao.NA_FILA);
        assertThat(e.getHistorico()).containsExactly(new HistoricoEtapa(EtapaExecucao.NA_FILA, T0));
        assertThat(e.getId()).isNotNull();
        assertThat(e.getOsId()).isEqualTo(OS);
        assertThat(e.getSagaId()).isEqualTo(SAGA);
        assertThat(e.getServicos()).isEqualTo(SERVICOS);
        assertThat(e.getObservacoes()).isEqualTo("obs");
        assertThat(e.getMotivoRejeicao()).isNull();
        assertThat(e.getCriadaEm()).isEqualTo(T0);
        assertThat(e.getAtualizadaEm()).isEqualTo(T0);
    }

    @Test
    void avancaPeloFluxoCompleto() {
        var e = nova();
        e.avancarPara(EtapaExecucao.EM_REPARO, T1);
        e.avancarPara(EtapaExecucao.EM_TESTE, T2);
        e.avancarPara(EtapaExecucao.CONCLUIDA, T3);
        assertThat(e.getEtapa()).isEqualTo(EtapaExecucao.CONCLUIDA);
        assertThat(e.getHistorico()).hasSize(4);
        assertThat(e.getHistorico().get(3)).isEqualTo(new HistoricoEtapa(EtapaExecucao.CONCLUIDA, T3));
        assertThat(e.getAtualizadaEm()).isEqualTo(T3);
        assertThat(e.getCriadaEm()).isEqualTo(T0);
    }

    @Test
    void testeReprovadoVoltaParaReparo() {
        var e = nova();
        e.avancarPara(EtapaExecucao.EM_REPARO, T1);
        e.avancarPara(EtapaExecucao.EM_TESTE, T2);
        e.avancarPara(EtapaExecucao.EM_REPARO, T3);
        assertThat(e.getEtapa()).isEqualTo(EtapaExecucao.EM_REPARO);
        assertThat(e.getHistorico()).hasSize(4);
    }

    @Test
    void pularEtapaLancaExcecao() {
        var nova = nova();
        assertThatThrownBy(() -> nova.avancarPara(EtapaExecucao.CONCLUIDA, T1))
                .isInstanceOf(TransicaoEtapaInvalidaException.class);
        assertThat(nova.getEtapa()).isEqualTo(EtapaExecucao.NA_FILA);
        assertThat(nova.getHistorico()).hasSize(1);
    }

    @Test
    void etapaTerminalNaoPermiteMovimento() {
        var e = nova();
        e.avancarPara(EtapaExecucao.EM_REPARO, T1);
        e.avancarPara(EtapaExecucao.EM_TESTE, T2);
        e.avancarPara(EtapaExecucao.CONCLUIDA, T3);
        assertThatThrownBy(() -> e.avancarPara(EtapaExecucao.EM_REPARO, T3.plusSeconds(1)))
                .isInstanceOf(TransicaoEtapaInvalidaException.class);
    }

    @Test
    void rejeitadaETerminal() {
        var e = Execucao.rejeitada(OS, SAGA, "Sem peça", T0);
        assertThat(e.getEtapa()).isEqualTo(EtapaExecucao.REJEITADA);
        assertThat(e.getEtapa().terminal()).isTrue();
        assertThat(e.getMotivoRejeicao()).isEqualTo("Sem peça");
        assertThat(e.getId()).isNotNull();
        assertThat(e.getServicos()).isEmpty();
        assertThat(e.getHistorico()).containsExactly(new HistoricoEtapa(EtapaExecucao.REJEITADA, T0));
        assertThatThrownBy(() -> e.avancarPara(EtapaExecucao.EM_REPARO, T1))
                .isInstanceOf(TransicaoEtapaInvalidaException.class);
    }

    @Test
    void novaSemServicosLancaIllegalArgument() {
        assertThatThrownBy(() -> Execucao.nova(OS, SAGA, List.of(), null, T0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Execucao.nova(OS, SAGA, null, null, T0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void idsNulosLancamIllegalArgument() {
        assertThatThrownBy(() -> Execucao.nova(null, SAGA, SERVICOS, null, T0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Execucao.nova(OS, null, SERVICOS, null, T0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Execucao.rejeitada(null, SAGA, "x", T0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Execucao.rejeitada(OS, null, "x", T0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejeitadaSemMotivoLancaIllegalArgument() {
        assertThatThrownBy(() -> Execucao.rejeitada(OS, SAGA, " ", T0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Execucao.rejeitada(OS, SAGA, null, T0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void servicoComDadosInvalidosLancaIllegalArgument() {
        assertThatThrownBy(() -> new ServicoExecucao("x", 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ServicoExecucao("x", -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ServicoExecucao(" ", 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void listasDosGettersSaoImutaveis() {
        var e = nova();
        assertThatThrownBy(() -> e.getServicos().add(new ServicoExecucao("x", 1)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> e.getHistorico().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void reconstituirRestauraTodosOsCampos() {
        var id = UUID.randomUUID();
        var historico = new ArrayList<>(List.of(new HistoricoEtapa(EtapaExecucao.NA_FILA, T0)));
        var e = Execucao.reconstituir(id, OS, SAGA, EtapaExecucao.EM_REPARO, SERVICOS, "obs", null, historico, T0, T1);
        assertThat(e.getId()).isEqualTo(id);
        assertThat(e.getOsId()).isEqualTo(OS);
        assertThat(e.getSagaId()).isEqualTo(SAGA);
        assertThat(e.getEtapa()).isEqualTo(EtapaExecucao.EM_REPARO);
        assertThat(e.getServicos()).isEqualTo(SERVICOS);
        assertThat(e.getObservacoes()).isEqualTo("obs");
        assertThat(e.getHistorico()).isEqualTo(historico);
        assertThat(e.getCriadaEm()).isEqualTo(T0);
        assertThat(e.getAtualizadaEm()).isEqualTo(T1);
        historico.clear();
        assertThat(e.getHistorico()).hasSize(1);
    }
}
