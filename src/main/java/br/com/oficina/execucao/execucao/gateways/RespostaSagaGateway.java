package br.com.oficina.execucao.execucao.gateways;

import java.time.Instant;
import java.util.UUID;

public interface RespostaSagaGateway {

    void execucaoAceita(UUID sagaId, UUID osId, UUID execucaoId, String correlationId);

    void execucaoRejeitada(UUID sagaId, UUID osId, String motivo, String correlationId);

    void execucaoFinalizada(UUID sagaId, UUID osId, UUID execucaoId, Instant em, String correlationId);
}
