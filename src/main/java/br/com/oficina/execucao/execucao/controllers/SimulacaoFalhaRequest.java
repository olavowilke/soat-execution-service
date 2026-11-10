package br.com.oficina.execucao.execucao.controllers;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record SimulacaoFalhaRequest(
        @NotNull @Schema(description = "Se true, o próximo IniciarExecucao é rejeitado com motivo SIMULACAO",
                example = "true") Boolean ativa) {
}
