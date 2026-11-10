package br.com.oficina.execucao.execucao.usecases;

import br.com.oficina.execucao.execucao.entities.EtapaExecucao;
import br.com.oficina.execucao.execucao.entities.Execucao;
import br.com.oficina.execucao.execucao.entities.HistoricoEtapa;
import br.com.oficina.execucao.execucao.entities.ServicoExecucao;
import br.com.oficina.execucao.execucao.entities.TransicaoEtapaInvalidaException;
import br.com.oficina.execucao.execucao.gateways.ExecucaoAlteradaConcorrentementeException;
import br.com.oficina.execucao.execucao.gateways.ExecucaoGateway;
import br.com.oficina.execucao.execucao.gateways.RespostaSagaGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AvancarEtapaUseCaseTest {

    private static final UUID SAGA = UUID.randomUUID();
    private static final UUID OS = UUID.randomUUID();
    private static final Instant ANTES = Instant.parse("2026-11-09T11:00:00Z");
    private static final Instant AGORA = Instant.parse("2026-11-09T12:00:00Z");

    @Mock
    private ExecucaoGateway execucoes;
    @Mock
    private RespostaSagaGateway respostas;

    private AvancarEtapaUseCase useCase;

    @BeforeEach
    void preparar() {
        useCase = new AvancarEtapaUseCase(execucoes, respostas, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private Execucao em(EtapaExecucao etapa) {
        return Execucao.reconstituir(UUID.randomUUID(), OS, SAGA, etapa,
                List.of(new ServicoExecucao("Troca de oleo", 1)), "obs", null,
                List.of(new HistoricoEtapa(etapa, ANTES)), ANTES, ANTES, 3L);
    }

    @Test
    void avancaEPersisteSemPublicarAntesDeConcluir() {
        Execucao execucao = em(EtapaExecucao.NA_FILA);
        when(execucoes.buscarPorOsId(OS)).thenReturn(Optional.of(execucao));

        Execucao resultado = useCase.execute(OS, EtapaExecucao.EM_REPARO);

        assertThat(resultado.getEtapa()).isEqualTo(EtapaExecucao.EM_REPARO);
        assertThat(resultado.getAtualizadaEm()).isEqualTo(AGORA);
        verify(execucoes).atualizar(execucao);
        verify(respostas, never()).execucaoFinalizada(any(), any(), any(), any());
    }

    @Test
    void concluirPublicaFinalizadaDepoisDePersistir() {
        Execucao execucao = em(EtapaExecucao.EM_TESTE);
        when(execucoes.buscarPorOsId(OS)).thenReturn(Optional.of(execucao));

        useCase.execute(OS, EtapaExecucao.CONCLUIDA);

        var ordem = inOrder(execucoes, respostas);
        ordem.verify(execucoes).atualizar(execucao);
        ordem.verify(respostas).execucaoFinalizada(SAGA, OS, execucao.getId(), AGORA);
    }

    @Test
    void concluirDeNovoReenviaFinalizada() {
        Execucao concluida = em(EtapaExecucao.CONCLUIDA);
        when(execucoes.buscarPorOsId(OS)).thenReturn(Optional.of(concluida));

        Execucao resultado = useCase.execute(OS, EtapaExecucao.CONCLUIDA);

        assertThat(resultado).isSameAs(concluida);
        verify(respostas).execucaoFinalizada(SAGA, OS, concluida.getId(), ANTES);
        verify(execucoes, never()).atualizar(any());
    }

    @Test
    void osDesconhecidaLancaNaoEncontrada() {
        when(execucoes.buscarPorOsId(OS)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(OS, EtapaExecucao.EM_REPARO))
                .isInstanceOf(ExecucaoNaoEncontradaException.class)
                .hasMessageContaining(OS.toString());
    }

    @Test
    void transicaoInvalidaNaoPersisteNemPublica() {
        when(execucoes.buscarPorOsId(OS)).thenReturn(Optional.of(em(EtapaExecucao.NA_FILA)));

        assertThatThrownBy(() -> useCase.execute(OS, EtapaExecucao.CONCLUIDA))
                .isInstanceOf(TransicaoEtapaInvalidaException.class);
        verify(execucoes, never()).atualizar(any());
        verify(respostas, never()).execucaoFinalizada(any(), any(), any(), any());
    }

    @Test
    void rejeitadaNaoAvanca() {
        when(execucoes.buscarPorOsId(OS)).thenReturn(Optional.of(em(EtapaExecucao.REJEITADA)));

        assertThatThrownBy(() -> useCase.execute(OS, EtapaExecucao.CONCLUIDA))
                .isInstanceOf(TransicaoEtapaInvalidaException.class);
    }

    @Test
    void conflitoDeVersaoNaoPublica() {
        Execucao execucao = em(EtapaExecucao.EM_TESTE);
        when(execucoes.buscarPorOsId(OS)).thenReturn(Optional.of(execucao));
        doThrow(new ExecucaoAlteradaConcorrentementeException(OS, null)).when(execucoes).atualizar(execucao);

        assertThatThrownBy(() -> useCase.execute(OS, EtapaExecucao.CONCLUIDA))
                .isInstanceOf(ExecucaoAlteradaConcorrentementeException.class);
        verify(respostas, never()).execucaoFinalizada(any(), any(), any(), any());
    }

    @Test
    void falhaAoPublicarPropagaDepoisDePersistir() {
        Execucao execucao = em(EtapaExecucao.EM_TESTE);
        when(execucoes.buscarPorOsId(OS)).thenReturn(Optional.of(execucao));
        doThrow(new IllegalStateException("sqs fora")).when(respostas)
                .execucaoFinalizada(any(), any(), any(), any());

        assertThatThrownBy(() -> useCase.execute(OS, EtapaExecucao.CONCLUIDA))
                .isInstanceOf(IllegalStateException.class);
        verify(execucoes).atualizar(execucao);
    }
}
