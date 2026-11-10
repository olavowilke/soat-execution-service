package br.com.oficina.execucao.execucao.usecases;

import br.com.oficina.execucao.execucao.entities.EtapaExecucao;
import br.com.oficina.execucao.execucao.entities.Execucao;
import br.com.oficina.execucao.execucao.entities.ServicoExecucao;
import br.com.oficina.execucao.execucao.gateways.ExecucaoGateway;
import br.com.oficina.execucao.execucao.gateways.SimulacaoFalhaGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsultasEFlagUseCasesTest {

    private static final Instant AGORA = Instant.parse("2026-11-09T12:00:00Z");

    @Mock
    private ExecucaoGateway execucoes;
    @Mock
    private SimulacaoFalhaGateway simulacao;

    private Execucao nova() {
        return Execucao.nova(UUID.randomUUID(), UUID.randomUUID(), List.of(new ServicoExecucao("x", 1)), null, AGORA);
    }

    @Test
    void filaDevolveAtivasDoGateway() {
        List<Execucao> ativas = List.of(nova(), nova());
        when(execucoes.listarAtivas()).thenReturn(ativas);

        assertThat(new ListarFilaUseCase(execucoes).execute()).containsExactlyElementsOf(ativas);
    }

    @Test
    void buscaDevolveExecucao() {
        Execucao e = nova();
        when(execucoes.buscarPorOsId(e.getOsId())).thenReturn(Optional.of(e));

        assertThat(new BuscarExecucaoUseCase(execucoes).execute(e.getOsId()).getEtapa())
                .isEqualTo(EtapaExecucao.NA_FILA);
    }

    @Test
    void buscaSemResultadoLancaNaoEncontrada() {
        UUID os = UUID.randomUUID();
        when(execucoes.buscarPorOsId(os)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new BuscarExecucaoUseCase(execucoes).execute(os))
                .isInstanceOf(ExecucaoNaoEncontradaException.class);
    }

    @Test
    void definirSimulacaoGravaFlag() {
        new DefinirSimulacaoFalhaUseCase(simulacao).execute(true);

        verify(simulacao).definir(true);
    }
}
