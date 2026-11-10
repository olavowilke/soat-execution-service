package br.com.oficina.execucao.execucao.presenters;

import br.com.oficina.execucao.execucao.entities.EtapaExecucao;

import java.time.Instant;
import java.util.UUID;

public record ExecucaoResumoResponse(UUID osId, EtapaExecucao etapa, Instant criadaEm) {
}
