package br.com.oficina.execucao.shared.infrastructure.mensageria;

import br.com.oficina.execucao.execucao.entities.ServicoExecucao;
import br.com.oficina.execucao.execucao.usecases.IniciarExecucaoCommand;
import br.com.oficina.execucao.execucao.usecases.IniciarExecucaoUseCase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.newrelic.api.agent.Trace;
import io.awspring.cloud.sqs.annotation.SqsListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Consome os comandos da Saga. Modo de ack ON_SUCCESS: qualquer excecao deixa a
 * mensagem na fila, que a entrega de novo e, depois de 3 tentativas, manda para a DLQ.
 */
@Component
public class ComandosListener {

    static final String TIPO_INICIAR_EXECUCAO = "IniciarExecucao";

    private static final Logger log = LoggerFactory.getLogger(ComandosListener.class);

    private final ObjectMapper mapper;
    private final IniciarExecucaoUseCase iniciarExecucao;
    private final TracingMensagens tracing;

    public ComandosListener(ObjectMapper mapper, IniciarExecucaoUseCase iniciarExecucao,
            TracingMensagens tracing) {
        this.tracing = tracing;
        this.mapper = mapper;
        this.iniciarExecucao = iniciarExecucao;
    }

    @Trace(dispatcher = true)
    @SqsListener(value = "${execucao.filas.comandos}", acknowledgementMode = "ON_SUCCESS")
    public void receber(String corpo,
            @Header(name = TracingMensagens.ATRIBUTO_NEWRELIC, required = false) String newrelic) {
        MensagemSaga mensagem = ler(corpo);
        try {
            boolean semCorrelationId = mensagem.correlationId() == null || mensagem.correlationId().isBlank();
            MDC.put("correlationId", semCorrelationId ? UUID.randomUUID().toString() : mensagem.correlationId());
            MDC.put("sagaId", String.valueOf(mensagem.sagaId()));
            if (newrelic != null) {
                tracing.aceitar(Map.of(TracingMensagens.ATRIBUTO_NEWRELIC, newrelic));
            }
            if (!TIPO_INICIAR_EXECUCAO.equals(mensagem.type())) {
                log.error("Tipo de mensagem desconhecido {}, messageId={}", mensagem.type(), mensagem.messageId());
                throw new IllegalArgumentException("Tipo de mensagem desconhecido: " + mensagem.type());
            }
            iniciarExecucao.execute(paraComando(mensagem));
        } finally {
            MDC.remove("correlationId");
            MDC.remove("sagaId");
        }
    }

    private MensagemSaga ler(String corpo) {
        try {
            MensagemSaga mensagem = mapper.readValue(corpo, MensagemSaga.class);
            if (mensagem.payload() == null || mensagem.payload().isNull()) {
                throw new IllegalArgumentException("Envelope sem payload");
            }
            if (mensagem.sagaId() == null || mensagem.osId() == null || mensagem.type() == null) {
                throw new IllegalArgumentException("Envelope sem sagaId, osId ou type");
            }
            return mensagem;
        } catch (Exception e) {
            log.error("Mensagem invalida, nao confirmada: {} corpo={}", e.getMessage(), resumo(corpo));
            throw new IllegalArgumentException("Mensagem invalida", e);
        }
    }

    private static String resumo(String corpo) {
        return corpo == null ? null : corpo.substring(0, Math.min(corpo.length(), 200));
    }

    private IniciarExecucaoCommand paraComando(MensagemSaga mensagem) {
        JsonNode payload = mensagem.payload();
        List<ServicoExecucao> servicos = new ArrayList<>();
        for (JsonNode s : payload.path("servicos")) {
            servicos.add(new ServicoExecucao(s.path("descricao").asText(null), s.path("quantidade").asInt()));
        }
        JsonNode obs = payload.path("observacoes");
        return new IniciarExecucaoCommand(mensagem.sagaId(), mensagem.osId(), servicos,
                obs.isNull() || obs.isMissingNode() ? null : obs.asText());
    }
}
