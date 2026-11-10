package br.com.oficina.execucao.execucao.usecases;

import br.com.oficina.execucao.execucao.entities.ServicoExecucao;

import java.util.List;
import java.util.UUID;

public record IniciarExecucaoCommand(UUID sagaId, UUID osId, String correlationId, List<ServicoExecucao> servicos, String observacoes) {
}
