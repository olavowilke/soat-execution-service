package br.com.oficina.execucao.execucao.gateways;

public interface SimulacaoFalhaGateway {

    void definir(boolean ativa);

    /** Retorna {@code true} só para o chamador que desligou a flag. */
    boolean consumir();
}
