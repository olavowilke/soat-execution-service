package br.com.oficina.execucao.shared.infrastructure.mensageria;

import br.com.oficina.execucao.execucao.gateways.ExecucaoGateway;
import br.com.oficina.execucao.execucao.gateways.RespostaSagaGateway;
import br.com.oficina.execucao.shared.LocalStackIT;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class ComandosListenerIT extends LocalStackIT {

    @Autowired
    private SqsAsyncClient sqs;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private ExecucaoGateway execucoes;
    @MockitoSpyBean
    private RespostaSagaGateway respostasSpy;
    @MockitoSpyBean
    private TracingMensagens tracingSpy;
    @Value("${execucao.filas.comandos}")
    private String filaComandos;
    @Value("${execucao.filas.respostas}")
    private String filaRespostas;

    @DynamicPropertySource
    static void filaComandosReal(DynamicPropertyRegistry registry) {
        registry.add("execucao.filas.comandos", () -> FILA_COMANDOS);
    }

    private ListAppender<ILoggingEvent> logs;
    private List<Logger> loggers;

    @BeforeEach
    void capturarLogs() {
        loggers = List.of((Logger) LoggerFactory.getLogger(ComandosListener.class),
                (Logger) LoggerFactory.getLogger(
                        "io.awspring.cloud.sqs.listener.sink.AbstractMessageProcessingPipelineSink"));
        logs = new ListAppender<>();
        logs.start();
        loggers.forEach(l -> l.addAppender(logs));
        // Captura os ERROR esperados e tira do console: o appender fica so no logger do teste.
        loggers.forEach(l -> l.setAdditive(false));
    }

    @AfterEach
    void soltarLogs() {
        loggers.forEach(l -> {
            l.detachAppender(logs);
            l.setAdditive(true);
        });
    }

    @Test
    void comandoNaFilaGeraRespostaNaFilaDeRespostas() {
        UUID saga = UUID.randomUUID();
        UUID os = UUID.randomUUID();

        enviar(comando(saga, os, "IniciarExecucao"));

        Message resposta = aguardarRespostas(os, 1).get(0);
        assertThat(resposta.messageAttributes().get("type").stringValue()).isEqualTo("ExecucaoAceita");
        assertThat(resposta.messageAttributes().get("correlationId").stringValue()).isEqualTo("corr-" + saga);
        JsonNode corpo = lerJson(resposta);
        assertThat(corpo.get("sagaId").asText()).isEqualTo(saga.toString());
        assertThat(corpo.get("osId").asText()).isEqualTo(os.toString());
        assertThat(corpo.get("type").asText()).isEqualTo("ExecucaoAceita");
        assertThat(corpo.get("correlationId").asText()).isEqualTo("corr-" + saga);
        assertThat(UUID.fromString(corpo.get("messageId").asText())).isNotNull();
        assertThat(Instant.parse(corpo.get("occurredAt").asText())).isNotNull();
        UUID execucaoId = execucoes.buscarPorOsId(os).orElseThrow().getId();
        assertThat(corpo.get("payload").get("execucaoId").asText()).isEqualTo(execucaoId.toString());
    }


    @Test
    void comandoSemServicosGeraExecucaoAceita() {
        UUID saga = UUID.randomUUID();
        UUID os = UUID.randomUUID();

        enviar(comando(saga, os, "IniciarExecucao", "corr-" + saga, List.of()));

        Message resposta = aguardarRespostas(os, 1).get(0);
        assertThat(resposta.messageAttributes().get("type").stringValue()).isEqualTo("ExecucaoAceita");
        assertThat(execucoes.buscarPorOsId(os).orElseThrow().getServicos()).isEmpty();
    }

    @Test
    void tipoDesconhecidoNaoGeraResposta() {
        UUID saga = UUID.randomUUID();
        UUID os = UUID.randomUUID();

        enviar(comando(saga, os, "TipoQueNaoExiste"));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(new ArrayList<>(logs.list)).anyMatch(e -> e.getLevel() == Level.ERROR
                        && e.getFormattedMessage().contains("TipoQueNaoExiste")));
        assertThat(execucoes.buscarPorOsId(os)).isEmpty();
    }

    @Test
    void falhaAoPublicarNaoConfirmaMensagem() {
        UUID saga = UUID.randomUUID();
        UUID os = UUID.randomUUID();
        doThrow(new IllegalStateException("falha simulada")).doCallRealMethod()
                .when(respostasSpy).execucaoAceita(any(), any(), any(), any());

        enviar(comando(saga, os, "IniciarExecucao"));

        Message resposta = aguardarRespostas(os, 1).get(0);
        assertThat(resposta.messageAttributes().get("type").stringValue()).isEqualTo("ExecucaoAceita");
        verify(respostasSpy, times(2)).execucaoAceita(any(), any(), any(), any());
    }

    @Test
    void mensagemInvalidaNaoDerrubaListenerEVaiParaDlq() {
        UUID saga = UUID.randomUUID();
        UUID os = UUID.randomUUID();

        enviar("{not json");
        enviar(comando(saga, os, "IniciarExecucao", "corr-" + saga));

        aguardarRespostas(os, 1);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(new ArrayList<>(logs.list)).anyMatch(e -> e.getLevel() == Level.ERROR
                        && e.getFormattedMessage().contains("Mensagem invalida")
                        && e.getFormattedMessage().contains("{not json")));
        assertThat(aguardarNaDlq("{not json")).isTrue();
    }

    @Test
    void envelopeSemPayloadGeraErroClaro() {
        enviar("{\"messageId\":\"" + UUID.randomUUID() + "\",\"sagaId\":\"" + UUID.randomUUID()
                + "\",\"osId\":\"" + UUID.randomUUID() + "\",\"type\":\"IniciarExecucao\"}");

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(new ArrayList<>(logs.list)).anyMatch(e -> e.getLevel() == Level.ERROR
                        && e.getFormattedMessage().contains("Envelope sem payload")));
    }

    @Test
    void comandoSemCorrelationIdRecebeRespostaComCorrelationIdGerado() {
        UUID saga = UUID.randomUUID();
        UUID os = UUID.randomUUID();

        enviar(comando(saga, os, "IniciarExecucao", null));

        Message resposta = aguardarRespostas(os, 1).get(0);
        String anexo = resposta.messageAttributes().get("correlationId").stringValue();
        assertThat(anexo).isNotBlank();
        assertThat(lerJson(resposta).get("correlationId").asText()).isEqualTo(anexo);
    }

    @Test
    void atributoNewRelicChegaAoTracing() {
        UUID saga = UUID.randomUUID();
        UUID os = UUID.randomUUID();

        enviar(comando(saga, os, "IniciarExecucao", "corr-" + saga), Map.of("newrelic",
                MessageAttributeValue.builder().dataType("String").stringValue("cabecalho-nr").build()));

        aguardarRespostas(os, 1);
        verify(tracingSpy).aceitar(Map.of("newrelic", "cabecalho-nr"));
    }

    private String comando(UUID saga, UUID os, String tipo) {
        return comando(saga, os, tipo, "corr-" + saga);
    }

    private String comando(UUID saga, UUID os, String tipo, String correlationId) {
        return comando(saga, os, tipo, correlationId,
                List.of(Map.of("descricao", "Troca de oleo", "quantidade", 1)));
    }

    private String comando(UUID saga, UUID os, String tipo, String correlationId, List<Map<String, Object>> servicos) {
        Map<String, Object> envelope = new HashMap<>();
        envelope.put("messageId", UUID.randomUUID());
        envelope.put("sagaId", saga);
        envelope.put("osId", os);
        envelope.put("type", tipo);
        if (correlationId != null) {
            envelope.put("correlationId", correlationId);
        }
        envelope.put("occurredAt", Instant.now().toString());
        envelope.put("payload", Map.of(
                "servicos", servicos,
                "observacoes", "obs"));
        try {
            return mapper.writeValueAsString(envelope);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void enviar(String corpo, Map<String, MessageAttributeValue> extras) {
        String url = sqs.getQueueUrl(b -> b.queueName(filaComandos)).join().queueUrl();
        Map<String, MessageAttributeValue> atributos = new HashMap<>(extras);
        atributos.put("type", MessageAttributeValue.builder().dataType("String").stringValue("IniciarExecucao").build());
        sqs.sendMessage(b -> b.queueUrl(url).messageBody(corpo).messageAttributes(atributos)).join();
    }

    /** Le a DLQ ate achar o corpo. Cada mensagem leva 3 recebimentos de 2s antes de chegar la. */
    private boolean aguardarNaDlq(String corpo) {
        String url = sqs.getQueueUrl(b -> b.queueName(FILA_COMANDOS_DLQ)).join().queueUrl();
        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(500)).until(() ->
                sqs.receiveMessage(b -> b.queueUrl(url).maxNumberOfMessages(10).waitTimeSeconds(1))
                        .join().messages().stream().anyMatch(m -> corpo.equals(m.body())));
        return true;
    }

    private void enviar(String corpo) {
        String url = sqs.getQueueUrl(b -> b.queueName(filaComandos)).join().queueUrl();
        sqs.sendMessage(b -> b.queueUrl(url).messageBody(corpo)
                .messageAttributes(Map.of("type", MessageAttributeValue.builder()
                        .dataType("String").stringValue("IniciarExecucao").build()))).join();
    }

    /** Le a fila de respostas ate achar {@code quantidade} mensagens do osId. Descarta as de outros testes. */
    private List<Message> aguardarRespostas(UUID os, int quantidade) {
        String url = sqs.getQueueUrl(b -> b.queueName(filaRespostas)).join().queueUrl();
        List<Message> achadas = new ArrayList<>();
        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(200)).until(() -> {
            sqs.receiveMessage(b -> b.queueUrl(url).maxNumberOfMessages(10).waitTimeSeconds(1)
                            .messageAttributeNames("All"))
                    .join().messages().forEach(m -> {
                        if (lerJson(m).path("osId").asText().equals(os.toString())) {
                            achadas.add(m);
                        }
                        sqs.deleteMessage(b -> b.queueUrl(url).receiptHandle(m.receiptHandle())).join();
                    });
            return achadas.size() >= quantidade;
        });
        return achadas;
    }

    private JsonNode lerJson(Message m) {
        try {
            return mapper.readTree(m.body());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
