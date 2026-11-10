package br.com.oficina.execucao.execucao.entities;

import java.time.Instant;

public record HistoricoEtapa(EtapaExecucao etapa, Instant em) {
}
