package br.com.oficina.execucao.execucao.controllers;

import br.com.oficina.execucao.execucao.entities.EtapaExecucao;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record AvancarEtapaRequest(
        @NotNull @Schema(description = "Etapa de destino", example = "EM_REPARO") EtapaExecucao etapa) {
}
