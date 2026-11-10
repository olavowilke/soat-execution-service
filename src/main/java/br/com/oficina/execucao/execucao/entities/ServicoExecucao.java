package br.com.oficina.execucao.execucao.entities;

public record ServicoExecucao(String descricao, int quantidade) {

    public ServicoExecucao {
        if (descricao == null || descricao.isBlank()) {
            throw new IllegalArgumentException("descricao do servico e obrigatoria");
        }
        if (quantidade <= 0) {
            throw new IllegalArgumentException("quantidade deve ser maior que zero");
        }
    }
}
