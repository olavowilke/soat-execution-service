package br.com.oficina.execucao.execucao.usecases;

import br.com.oficina.execucao.execucao.gateways.SimulacaoFalhaGateway;

public class DefinirSimulacaoFalhaUseCase {

    private final SimulacaoFalhaGateway simulacao;

    public DefinirSimulacaoFalhaUseCase(SimulacaoFalhaGateway simulacao) {
        this.simulacao = simulacao;
    }

    public void execute(boolean ativa) {
        simulacao.definir(ativa);
    }
}
