package br.com.oficina.execucao.execucao.entities;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EtapaExecucaoTest {

    @Test
    void proximasSegueOFluxo() {
        assertThat(EtapaExecucao.NA_FILA.proximas()).containsExactly(EtapaExecucao.EM_REPARO);
        assertThat(EtapaExecucao.EM_REPARO.proximas()).containsExactly(EtapaExecucao.EM_TESTE);
        assertThat(EtapaExecucao.EM_TESTE.proximas())
                .containsExactlyInAnyOrder(EtapaExecucao.CONCLUIDA, EtapaExecucao.EM_REPARO);
    }

    @Test
    void etapasTerminaisNaoTemProximas() {
        assertThat(EtapaExecucao.CONCLUIDA.proximas()).isEmpty();
        assertThat(EtapaExecucao.REJEITADA.proximas()).isEmpty();
        assertThat(EtapaExecucao.CONCLUIDA.terminal()).isTrue();
        assertThat(EtapaExecucao.REJEITADA.terminal()).isTrue();
    }

    @Test
    void etapasAbertasNaoSaoTerminais() {
        assertThat(EtapaExecucao.NA_FILA.terminal()).isFalse();
        assertThat(EtapaExecucao.EM_REPARO.terminal()).isFalse();
        assertThat(EtapaExecucao.EM_TESTE.terminal()).isFalse();
    }
}
