package br.com.oficina.execucao.execucao.usecases;

import br.com.oficina.execucao.execucao.entities.Execucao;
import br.com.oficina.execucao.execucao.gateways.ExecucaoGateway;

import java.util.List;

public class ListarFilaUseCase {

    private final ExecucaoGateway execucoes;

    public ListarFilaUseCase(ExecucaoGateway execucoes) {
        this.execucoes = execucoes;
    }

    /** Execuções ativas, da mais antiga para a mais nova. */
    public List<Execucao> execute() {
        return execucoes.listarAtivas();
    }
}
