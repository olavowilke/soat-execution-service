package br.com.oficina.execucao.shared.infrastructure.mensageria;

import br.com.oficina.execucao.execucao.gateways.RespostaSagaGateway;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Envia as respostas da Saga para a fila de respostas. O correlationId vem do MDC (o listener o define). */
@Component
public class RespostaSagaGatewaySqs implements RespostaSagaGateway {

    private static final String MDC_CORRELATION_ID = "correlationId";

    private final SqsTemplate sqs;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final String fila;

    public RespostaSagaGatewaySqs(SqsTemplate sqs, ObjectMapper mapper, Clock clock,
            @Value("${execucao.filas.respostas}") String fila) {
        this.sqs = sqs;
        this.mapper = mapper;
        this.clock = clock;
        this.fila = fila;
    }

    @Override
    public void execucaoAceita(UUID sagaId, UUID osId, UUID execucaoId) {
        ObjectNode payload = mapper.createObjectNode().put("execucaoId", execucaoId.toString());
        enviar("ExecucaoAceita", sagaId, osId, payload);
    }

    @Override
    public void execucaoRejeitada(UUID sagaId, UUID osId, String motivo) {
        ObjectNode payload = mapper.createObjectNode().put("motivo", motivo);
        enviar("ExecucaoRejeitada", sagaId, osId, payload);
    }

    @Override
    public void execucaoFinalizada(UUID sagaId, UUID osId, UUID execucaoId, Instant em) {
        ObjectNode payload = mapper.createObjectNode()
                .put("execucaoId", execucaoId.toString())
                .put("finalizadaEm", em.toString());
        enviar("ExecucaoFinalizada", sagaId, osId, payload);
    }

    private void enviar(String tipo, UUID sagaId, UUID osId, ObjectNode payload) {
        String correlationId = MDC.get(MDC_CORRELATION_ID);
        MensagemSaga mensagem = new MensagemSaga(UUID.randomUUID(), sagaId, osId, tipo, correlationId,
                clock.instant(), payload);
        String corpo;
        try {
            corpo = mapper.writeValueAsString(mensagem);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar " + tipo, e);
        }
        Map<String, String> trace = TracingMensagens.cabecalhosSaida();
        sqs.send(to -> {
            to.queue(fila).payload(corpo).header("type", tipo);
            if (correlationId != null) {
                to.header(MDC_CORRELATION_ID, correlationId);
            }
            trace.forEach(to::header);
        });
    }
}
