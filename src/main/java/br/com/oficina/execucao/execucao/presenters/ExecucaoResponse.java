package br.com.oficina.execucao.execucao.presenters;

import br.com.oficina.execucao.execucao.entities.EtapaExecucao;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ExecucaoResponse(
        UUID id,
        UUID osId,
        UUID sagaId,
        EtapaExecucao etapa,
        List<Servico> servicos,
        String observacoes,
        String motivoRejeicao,
        Instant criadaEm,
        Instant atualizadaEm,
        List<Historico> historico) {

    public record Servico(String descricao, int quantidade) {
    }

    public record Historico(EtapaExecucao etapa, Instant em) {
    }
}
