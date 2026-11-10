package br.com.oficina.execucao.execucao.usecases;

import java.util.UUID;

public class ExecucaoNaoEncontradaException extends RuntimeException {

    public ExecucaoNaoEncontradaException(UUID osId) {
        super("Execucao nao encontrada para a OS " + osId);
    }
}
