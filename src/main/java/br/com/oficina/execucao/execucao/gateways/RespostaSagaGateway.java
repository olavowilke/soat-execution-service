package br.com.oficina.execucao.execucao.gateways;

import java.time.Instant;
import java.util.UUID;

public interface RespostaSagaGateway {

    void execucaoAceita(UUID sagaId, UUID osId, UUID execucaoId);

    void execucaoRejeitada(UUID sagaId, UUID osId, String motivo);

    void execucaoFinalizada(UUID sagaId, UUID osId, UUID execucaoId, Instant em);
}
