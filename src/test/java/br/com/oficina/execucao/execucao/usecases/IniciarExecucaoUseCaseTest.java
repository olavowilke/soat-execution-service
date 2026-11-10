package br.com.oficina.execucao.execucao.usecases;

import br.com.oficina.execucao.execucao.entities.EtapaExecucao;
import br.com.oficina.execucao.execucao.entities.Execucao;
import br.com.oficina.execucao.execucao.entities.ServicoExecucao;
import br.com.oficina.execucao.execucao.gateways.ExecucaoGateway;
import br.com.oficina.execucao.execucao.gateways.RespostaSagaGateway;
import br.com.oficina.execucao.execucao.gateways.SimulacaoFalhaGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IniciarExecucaoUseCaseTest {

    private static final UUID SAGA = UUID.randomUUID();
    private static final UUID OS = UUID.randomUUID();
    private static final Instant AGORA = Instant.parse("2026-11-09T12:00:00Z");

    @Mock
    private ExecucaoGateway execucoes;
    @Mock
    private SimulacaoFalhaGateway simulacao;
    @Mock
    private RespostaSagaGateway respostas;

    private IniciarExecucaoUseCase useCase;
    private IniciarExecucaoCommand cmd;

    @BeforeEach
    void preparar() {
        useCase = new IniciarExecucaoUseCase(execucoes, simulacao, respostas,
                Clock.fixed(AGORA, ZoneOffset.UTC), 20);
        cmd = new IniciarExecucaoCommand(SAGA, OS, List.of(new ServicoExecucao("Troca de oleo", 1)), "obs");
    }

    @Test
    void aceitaQuandoHaCapacidade() {
        when(execucoes.buscarPorOsId(OS)).thenReturn(Optional.empty());
        when(simulacao.consumir()).thenReturn(false);
        when(execucoes.contarAtivas()).thenReturn(19L);
        when(execucoes.salvarNova(any())).thenReturn(true);

        Execucao resultado = useCase.execute(cmd);

        assertThat(resultado.getEtapa()).isEqualTo(EtapaExecucao.NA_FILA);
        assertThat(resultado.getCriadaEm()).isEqualTo(AGORA);
        verify(respostas).execucaoAceita(SAGA, OS, resultado.getId());
    }

    @Test
    void rejeitaPorSimulacao() {
        when(execucoes.buscarPorOsId(OS)).thenReturn(Optional.empty());
        when(simulacao.consumir()).thenReturn(true);
        when(execucoes.salvarNova(any())).thenReturn(true);

        useCase.execute(cmd);

        ArgumentCaptor<Execucao> salva = ArgumentCaptor.forClass(Execucao.class);
        verify(execucoes).salvarNova(salva.capture());
        assertThat(salva.getValue().getEtapa()).isEqualTo(EtapaExecucao.REJEITADA);
        assertThat(salva.getValue().getMotivoRejeicao()).isEqualTo("SIMULACAO");
        verify(respostas).execucaoRejeitada(SAGA, OS, "SIMULACAO");
        verify(execucoes, never()).contarAtivas();
    }

    @Test
    void rejeitaPorCapacidade() {
        when(execucoes.buscarPorOsId(OS)).thenReturn(Optional.empty());
        when(simulacao.consumir()).thenReturn(false);
        when(execucoes.contarAtivas()).thenReturn(20L);
        when(execucoes.salvarNova(any())).thenReturn(true);

        useCase.execute(cmd);

        ArgumentCaptor<Execucao> salva = ArgumentCaptor.forClass(Execucao.class);
        verify(execucoes).salvarNova(salva.capture());
        assertThat(salva.getValue().getEtapa()).isEqualTo(EtapaExecucao.REJEITADA);
        verify(respostas).execucaoRejeitada(SAGA, OS, "CAPACIDADE");
    }

    @Test
    void comandoRepetidoReenviaMesmaResposta() {
        Execucao existenteNaFila = Execucao.nova(OS, SAGA, cmd.servicos(), "obs", AGORA);
        when(execucoes.buscarPorOsId(OS)).thenReturn(Optional.of(existenteNaFila));

        Execucao resultado = useCase.execute(cmd);

        assertThat(resultado).isSameAs(existenteNaFila);
        verify(respostas).execucaoAceita(SAGA, OS, existenteNaFila.getId());
        verify(execucoes, never()).salvarNova(any());
        verify(simulacao, never()).consumir();
    }

    @Test
    void comandoRepetidoDeRejeitadaReenviaRejeicao() {
        Execucao rejeitada = Execucao.rejeitada(OS, SAGA, "CAPACIDADE", AGORA);
        when(execucoes.buscarPorOsId(OS)).thenReturn(Optional.of(rejeitada));

        useCase.execute(cmd);

        verify(respostas).execucaoRejeitada(SAGA, OS, "CAPACIDADE");
        verify(execucoes, never()).salvarNova(any());
        verify(simulacao, never()).consumir();
    }

    @Test
    void corridaPerdidaReenviaRespostaDaExecucaoVencedora() {
        Execucao vencedora = Execucao.nova(OS, SAGA, cmd.servicos(), "obs", AGORA);
        when(execucoes.buscarPorOsId(OS)).thenReturn(Optional.empty(), Optional.of(vencedora));
        when(simulacao.consumir()).thenReturn(false);
        when(execucoes.contarAtivas()).thenReturn(0L);
        when(execucoes.salvarNova(any())).thenReturn(false);

        Execucao resultado = useCase.execute(cmd);

        assertThat(resultado).isSameAs(vencedora);
        verify(respostas).execucaoAceita(SAGA, OS, vencedora.getId());
        verifyNoMoreInteractions(respostas);
    }
}
