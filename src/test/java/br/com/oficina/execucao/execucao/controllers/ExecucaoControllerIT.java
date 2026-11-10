package br.com.oficina.execucao.execucao.controllers;

import br.com.oficina.execucao.execucao.entities.EtapaExecucao;
import br.com.oficina.execucao.execucao.entities.Execucao;
import br.com.oficina.execucao.execucao.entities.ServicoExecucao;
import br.com.oficina.execucao.execucao.gateways.ExecucaoGateway;
import br.com.oficina.execucao.execucao.gateways.SimulacaoFalhaGateway;
import br.com.oficina.execucao.shared.LocalStackIT;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.Message;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExecucaoControllerIT extends LocalStackIT {

    /**
     * Fila propria de comandos: o ComandosListenerIT mantem outro contexto vivo sobre FILA_COMANDOS
     * e os dois listeners disputariam a mesma mensagem.
     */
    private static final String FILA_COMANDOS_API = FILA_COMANDOS + "-api";

    @DynamicPropertySource
    static void filaComandosPropria(DynamicPropertyRegistry registry) {
        registry.add("execucao.filas.comandos", () -> FILA_COMANDOS_API);
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private ExecucaoGateway execucoes;
    @Autowired
    private SimulacaoFalhaGateway simulacao;
    @Autowired
    private SqsAsyncClient sqs;

    @AfterEach
    void limparFlag() {
        simulacao.definir(false);
    }

    private Execucao criar(Instant criadaEm) {
        Execucao e = Execucao.nova(UUID.randomUUID(), UUID.randomUUID(),
                List.of(new ServicoExecucao("Troca de oleo", 2)), "obs", criadaEm);
        assertThat(execucoes.salvarNova(e)).isTrue();
        return execucoes.buscarPorOsId(e.getOsId()).orElseThrow();
    }

    private Execucao criarEm(EtapaExecucao etapa) throws Exception {
        Execucao e = criar(Instant.now());
        for (EtapaExecucao destino : caminhoAte(etapa)) {
            avancar(e.getOsId(), destino).andExpect(status().isOk());
        }
        return execucoes.buscarPorOsId(e.getOsId()).orElseThrow();
    }

    private List<EtapaExecucao> caminhoAte(EtapaExecucao etapa) {
        return switch (etapa) {
            case NA_FILA -> List.of();
            case EM_REPARO -> List.of(EtapaExecucao.EM_REPARO);
            case EM_TESTE -> List.of(EtapaExecucao.EM_REPARO, EtapaExecucao.EM_TESTE);
            case CONCLUIDA -> List.of(EtapaExecucao.EM_REPARO, EtapaExecucao.EM_TESTE, EtapaExecucao.CONCLUIDA);
            case REJEITADA -> throw new IllegalArgumentException();
        };
    }

    private org.springframework.test.web.servlet.ResultActions avancar(UUID os, EtapaExecucao etapa)
            throws Exception {
        return mvc.perform(post("/ordens/{os}/etapa", os).contentType(MediaType.APPLICATION_JSON)
                .content("{\"etapa\":\"" + etapa + "\"}"));
    }

    @Test
    void filaListaSoAtivasEmOrdem() throws Exception {
        Instant base = Instant.parse("2020-01-01T00:00:00Z");
        Execucao maisNova = criar(base.plusSeconds(20));
        Execucao maisAntiga = criar(base);
        Execucao concluida = criarEm(EtapaExecucao.CONCLUIDA);
        Execucao rejeitada = Execucao.rejeitada(UUID.randomUUID(), UUID.randomUUID(), "CAPACIDADE", base);
        execucoes.salvarNova(rejeitada);

        MvcResult resultado = mvc.perform(get("/fila")).andExpect(status().isOk()).andReturn();

        JsonNode fila = mapper.readTree(resultado.getResponse().getContentAsString());
        List<String> ids = new ArrayList<>();
        fila.forEach(n -> ids.add(n.get("osId").asText()));
        assertThat(ids).contains(maisAntiga.getOsId().toString(), maisNova.getOsId().toString())
                .doesNotContain(concluida.getOsId().toString(), rejeitada.getOsId().toString());
        assertThat(ids.indexOf(maisAntiga.getOsId().toString()))
                .isLessThan(ids.indexOf(maisNova.getOsId().toString()));
        JsonNode primeiro = fila.get(ids.indexOf(maisAntiga.getOsId().toString()));
        assertThat(primeiro.get("etapa").asText()).isEqualTo("NA_FILA");
        assertThat(Instant.parse(primeiro.get("criadaEm").asText())).isEqualTo(base);
    }

    @Test
    void detalheRetornaTodosOsCamposEHistorico() throws Exception {
        Execucao e = criar(Instant.parse("2026-11-09T10:00:00Z"));

        mvc.perform(get("/ordens/{os}", e.getOsId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(e.getId().toString()))
                .andExpect(jsonPath("$.osId").value(e.getOsId().toString()))
                .andExpect(jsonPath("$.sagaId").value(e.getSagaId().toString()))
                .andExpect(jsonPath("$.etapa").value("NA_FILA"))
                .andExpect(jsonPath("$.servicos[0].descricao").value("Troca de oleo"))
                .andExpect(jsonPath("$.servicos[0].quantidade").value(2))
                .andExpect(jsonPath("$.observacoes").value("obs"))
                .andExpect(jsonPath("$.criadaEm").value("2026-11-09T10:00:00Z"))
                .andExpect(jsonPath("$.historico[0].etapa").value("NA_FILA"))
                .andExpect(jsonPath("$.historico[0].em").value("2026-11-09T10:00:00Z"));
    }

    @Test
    void osDesconhecidaRetorna404() throws Exception {
        UUID os = UUID.randomUUID();

        mvc.perform(get("/ordens/{os}", os)).andExpect(status().isNotFound());
        avancar(os, EtapaExecucao.EM_REPARO).andExpect(status().isNotFound());
    }

    @Test
    void avancarEtapaRetorna200ComNovaEtapa() throws Exception {
        Execucao e = criar(Instant.now());

        avancar(e.getOsId(), EtapaExecucao.EM_REPARO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etapa").value("EM_REPARO"))
                .andExpect(jsonPath("$.historico.length()").value(2));
        assertThat(execucoes.buscarPorOsId(e.getOsId()).orElseThrow().getEtapa())
                .isEqualTo(EtapaExecucao.EM_REPARO);
    }

    @Test
    void transicaoInvalidaRetorna409() throws Exception {
        Execucao e = criar(Instant.now());

        avancar(e.getOsId(), EtapaExecucao.CONCLUIDA).andExpect(status().isConflict());
        assertThat(execucoes.buscarPorOsId(e.getOsId()).orElseThrow().getEtapa()).isEqualTo(EtapaExecucao.NA_FILA);
    }

    @Test
    void avancarExecucaoRejeitadaRetorna409() throws Exception {
        Execucao rejeitada = Execucao.rejeitada(UUID.randomUUID(), UUID.randomUUID(), "SIMULACAO", Instant.now());
        execucoes.salvarNova(rejeitada);

        avancar(rejeitada.getOsId(), EtapaExecucao.EM_REPARO).andExpect(status().isConflict());
    }

    @Test
    void etapaInexistenteNoCorpoRetorna400() throws Exception {
        Execucao e = criar(Instant.now());

        mvc.perform(post("/ordens/{os}/etapa", e.getOsId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"etapa\":\"XYZ\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void etapaAusenteNoCorpoRetorna422() throws Exception {
        Execucao e = criar(Instant.now());

        mvc.perform(post("/ordens/{os}/etapa", e.getOsId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.fields[0].field").value("etapa"));
    }

    @Test
    void osIdMalFormadoRetorna400() throws Exception {
        mvc.perform(get("/ordens/{os}", "nao-e-uuid")).andExpect(status().isBadRequest());
    }

    @Test
    void concluirPublicaFinalizadaEReenviaAoConcluirDeNovo() throws Exception {
        Execucao e = criarEm(EtapaExecucao.EM_TESTE);

        avancar(e.getOsId(), EtapaExecucao.CONCLUIDA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etapa").value("CONCLUIDA"));
        List<Message> primeira = aguardarRespostas(e.getOsId(), 1);
        JsonNode corpo = mapper.readTree(primeira.get(0).body());
        assertThat(corpo.get("type").asText()).isEqualTo("ExecucaoFinalizada");
        assertThat(corpo.get("sagaId").asText()).isEqualTo(e.getSagaId().toString());
        assertThat(corpo.get("payload").get("execucaoId").asText()).isEqualTo(e.getId().toString());

        avancar(e.getOsId(), EtapaExecucao.CONCLUIDA).andExpect(status().isOk());
        assertThat(aguardarRespostas(e.getOsId(), 1)).hasSize(1);
    }

    @Test
    void simulacaoDeFalhaAceitaFlagEDevolve204() throws Exception {
        mvc.perform(put("/admin/simulacao-falha").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ativa\":true}"))
                .andExpect(status().isNoContent());
        assertThat(simulacao.consumir()).isTrue();

        mvc.perform(put("/admin/simulacao-falha").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ativa\":false}"))
                .andExpect(status().isNoContent());
        assertThat(simulacao.consumir()).isFalse();
    }

    @Test
    void simulacaoDeFalhaSemCampoRetorna422() throws Exception {
        mvc.perform(put("/admin/simulacao-falha").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void simulacaoFalhaFazProximoComandoSerRejeitado() throws Exception {
        UUID saga = UUID.randomUUID();
        UUID os = UUID.randomUUID();
        mvc.perform(put("/admin/simulacao-falha").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ativa\":true}"))
                .andExpect(status().isNoContent());

        Map<String, Object> envelope = new HashMap<>();
        envelope.put("messageId", UUID.randomUUID());
        envelope.put("sagaId", saga);
        envelope.put("osId", os);
        envelope.put("type", "IniciarExecucao");
        envelope.put("correlationId", "corr-" + saga);
        envelope.put("occurredAt", Instant.now().toString());
        envelope.put("payload", Map.of("servicos", List.of(Map.of("descricao", "Troca de oleo", "quantidade", 1)),
                "observacoes", "obs"));
        String url = await().atMost(Duration.ofSeconds(15)).until(() -> {
            try {
                return sqs.getQueueUrl(b -> b.queueName(FILA_COMANDOS_API)).join().queueUrl();
            } catch (Exception ex) {
                return null;
            }
        }, u -> u != null);
        sqs.sendMessage(b -> b.queueUrl(url).messageBody(json(envelope))).join();

        JsonNode resposta = mapper.readTree(aguardarRespostas(os, 1).get(0).body());
        assertThat(resposta.get("type").asText()).isEqualTo("ExecucaoRejeitada");
        assertThat(resposta.get("payload").get("motivo").asText()).isEqualTo("SIMULACAO");
    }

    private String json(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Le a fila de respostas ate achar {@code quantidade} mensagens do osId. Descarta as de outros testes. */
    private List<Message> aguardarRespostas(UUID os, int quantidade) {
        String url = sqs.getQueueUrl(b -> b.queueName(FILA_RESPOSTAS)).join().queueUrl();
        List<Message> achadas = new ArrayList<>();
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(200)).until(() -> {
            sqs.receiveMessage(b -> b.queueUrl(url).maxNumberOfMessages(10).waitTimeSeconds(1))
                    .join().messages().forEach(m -> {
                        try {
                            if (mapper.readTree(m.body()).path("osId").asText().equals(os.toString())) {
                                achadas.add(m);
                            }
                        } catch (Exception ignorada) {
                            // corpo que nao e JSON nao e de interesse
                        }
                        sqs.deleteMessage(b -> b.queueUrl(url).receiptHandle(m.receiptHandle())).join();
                    });
            return achadas.size() >= quantidade;
        });
        return achadas;
    }
}
