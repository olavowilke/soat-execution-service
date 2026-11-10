package br.com.oficina.execucao.execucao.usecases;

import br.com.oficina.execucao.execucao.entities.EtapaExecucao;
import br.com.oficina.execucao.execucao.entities.Execucao;
import br.com.oficina.execucao.execucao.gateways.ExecucaoGateway;
import br.com.oficina.execucao.execucao.gateways.RespostaSagaGateway;
import br.com.oficina.execucao.execucao.gateways.SimulacaoFalhaGateway;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import static br.com.oficina.execucao.execucao.usecases.ResultadoInicioExecucao.MOTIVO_CAPACIDADE;
import static br.com.oficina.execucao.execucao.usecases.ResultadoInicioExecucao.MOTIVO_SIMULACAO;

public class IniciarExecucaoUseCase {

    private final ExecucaoGateway execucoes;
    private final SimulacaoFalhaGateway simulacao;
    private final RespostaSagaGateway respostas;
    private final Clock clock;
    private final int capacidadeMaxima;

    public IniciarExecucaoUseCase(ExecucaoGateway execucoes, SimulacaoFalhaGateway simulacao,
            RespostaSagaGateway respostas, Clock clock, int capacidadeMaxima) {
        this.execucoes = execucoes;
        this.simulacao = simulacao;
        this.respostas = respostas;
        this.clock = clock;
        this.capacidadeMaxima = capacidadeMaxima;
    }

    public Execucao execute(IniciarExecucaoCommand cmd) {
        Optional<Execucao> existente = execucoes.buscarPorOsId(cmd.osId());
        if (existente.isPresent()) {
            responder(cmd, existente.get());
            return existente.get();
        }

        Execucao nova = criar(cmd, clock.instant());
        if (!execucoes.salvarNova(nova)) {
            // Outra entrega gravou primeiro: repete a resposta dela, nunca outra.
            Execucao vencedora = execucoes.buscarPorOsId(cmd.osId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Execucao sumiu depois de conflito de gravacao, osId=" + cmd.osId()));
            responder(cmd, vencedora);
            return vencedora;
        }
        responder(cmd, nova);
        return nova;
    }

    private Execucao criar(IniciarExecucaoCommand cmd, Instant agora) {
        if (simulacao.consumir()) {
            return Execucao.rejeitada(cmd.osId(), cmd.sagaId(), MOTIVO_SIMULACAO, agora);
        }
        if (execucoes.contarAtivas() >= capacidadeMaxima) {
            return Execucao.rejeitada(cmd.osId(), cmd.sagaId(), MOTIVO_CAPACIDADE, agora);
        }
        return Execucao.nova(cmd.osId(), cmd.sagaId(), cmd.servicos(), cmd.observacoes(), agora);
    }

    private void responder(IniciarExecucaoCommand cmd, Execucao execucao) {
        if (execucao.getEtapa() == EtapaExecucao.REJEITADA) {
            respostas.execucaoRejeitada(cmd.sagaId(), cmd.osId(), execucao.getMotivoRejeicao());
        } else {
            respostas.execucaoAceita(cmd.sagaId(), cmd.osId(), execucao.getId());
        }
    }
}
