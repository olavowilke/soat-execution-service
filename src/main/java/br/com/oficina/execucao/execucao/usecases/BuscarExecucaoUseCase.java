package br.com.oficina.execucao.execucao.usecases;

import br.com.oficina.execucao.execucao.entities.Execucao;
import br.com.oficina.execucao.execucao.gateways.ExecucaoGateway;

import java.util.UUID;

public class BuscarExecucaoUseCase {

    private final ExecucaoGateway execucoes;

    public BuscarExecucaoUseCase(ExecucaoGateway execucoes) {
        this.execucoes = execucoes;
    }

    public Execucao execute(UUID osId) {
        return execucoes.buscarPorOsId(osId).orElseThrow(() -> new ExecucaoNaoEncontradaException(osId));
    }
}
