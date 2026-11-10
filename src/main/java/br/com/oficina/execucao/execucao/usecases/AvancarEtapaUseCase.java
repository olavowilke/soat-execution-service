package br.com.oficina.execucao.execucao.usecases;

import br.com.oficina.execucao.execucao.entities.EtapaExecucao;
import br.com.oficina.execucao.execucao.entities.Execucao;
import br.com.oficina.execucao.execucao.gateways.ExecucaoGateway;
import br.com.oficina.execucao.execucao.gateways.RespostaSagaGateway;

import java.time.Clock;
import java.util.UUID;

/**
 * Avança a etapa de uma execução. Grava primeiro e publica depois.
 * Se a publicação de ExecucaoFinalizada falhar, a etapa já está gravada como CONCLUIDA
 * e o erro sobe (HTTP 500). O staff repete o pedido CONCLUIDA: como a execução já está concluída,
 * o caso de uso só reenvia a mensagem.
 */
public class AvancarEtapaUseCase {

    private final ExecucaoGateway execucoes;
    private final RespostaSagaGateway respostas;
    private final Clock clock;

    public AvancarEtapaUseCase(ExecucaoGateway execucoes, RespostaSagaGateway respostas, Clock clock) {
        this.execucoes = execucoes;
        this.respostas = respostas;
        this.clock = clock;
    }

    public Execucao execute(UUID osId, EtapaExecucao destino) {
        Execucao execucao = execucoes.buscarPorOsId(osId)
                .orElseThrow(() -> new ExecucaoNaoEncontradaException(osId));

        if (execucao.getEtapa() == EtapaExecucao.CONCLUIDA && destino == EtapaExecucao.CONCLUIDA) {
            publicarFinalizada(execucao);
            return execucao;
        }

        execucao.avancarPara(destino, clock.instant());
        execucoes.atualizar(execucao);
        if (execucao.getEtapa() == EtapaExecucao.CONCLUIDA) {
            publicarFinalizada(execucao);
        }
        return execucao;
    }

    private void publicarFinalizada(Execucao execucao) {
        String correlationId = execucao.getCorrelationId() == null
                ? UUID.randomUUID().toString()
                : execucao.getCorrelationId();
        respostas.execucaoFinalizada(execucao.getSagaId(), execucao.getOsId(), execucao.getId(),
                execucao.getAtualizadaEm(), correlationId);
    }
}
