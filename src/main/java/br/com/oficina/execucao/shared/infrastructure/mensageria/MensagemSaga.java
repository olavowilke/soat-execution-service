package br.com.oficina.execucao.shared.infrastructure.mensageria;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

/** Envelope das mensagens da Saga. */
public record MensagemSaga(UUID messageId, UUID sagaId, UUID osId, String type, String correlationId,
        Instant occurredAt, JsonNode payload) {
}
