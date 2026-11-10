package br.com.oficina.execucao.execucao.entities;

import java.util.Set;

public enum EtapaExecucao {
    NA_FILA,
    EM_REPARO,
    EM_TESTE,
    CONCLUIDA,
    REJEITADA;

    public Set<EtapaExecucao> proximas() {
        return switch (this) {
            case NA_FILA -> Set.of(EM_REPARO);
            case EM_REPARO -> Set.of(EM_TESTE);
            case EM_TESTE -> Set.of(CONCLUIDA, EM_REPARO);
            case CONCLUIDA, REJEITADA -> Set.of();
        };
    }

    public boolean terminal() {
        return proximas().isEmpty();
    }
}
