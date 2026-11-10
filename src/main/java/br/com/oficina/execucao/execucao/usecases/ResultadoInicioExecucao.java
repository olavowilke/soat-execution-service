package br.com.oficina.execucao.execucao.usecases;

/** Motivos de rejeicao enviados a Saga. */
public final class ResultadoInicioExecucao {

    public static final String MOTIVO_SIMULACAO = "SIMULACAO";
    public static final String MOTIVO_CAPACIDADE = "CAPACIDADE";

    private ResultadoInicioExecucao() {
    }
}
