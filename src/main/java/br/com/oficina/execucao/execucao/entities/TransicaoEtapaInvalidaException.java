package br.com.oficina.execucao.execucao.entities;

public class TransicaoEtapaInvalidaException extends RuntimeException {

    public TransicaoEtapaInvalidaException(EtapaExecucao origem, EtapaExecucao destino) {
        super("Transicao de etapa invalida: " + origem + " -> " + destino);
    }
}
