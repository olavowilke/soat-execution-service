package br.com.oficina.execucao.execucao.controllers;

import br.com.oficina.execucao.execucao.presenters.ExecucaoPresenter;
import br.com.oficina.execucao.execucao.presenters.ExecucaoResponse;
import br.com.oficina.execucao.execucao.presenters.ExecucaoResumoResponse;
import br.com.oficina.execucao.execucao.usecases.AvancarEtapaUseCase;
import br.com.oficina.execucao.execucao.usecases.BuscarExecucaoUseCase;
import br.com.oficina.execucao.execucao.usecases.DefinirSimulacaoFalhaUseCase;
import br.com.oficina.execucao.execucao.usecases.ListarFilaUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** API do staff. Sem autenticação na aplicação: o acesso é por port-forward. */
@RestController
@Tag(name = "Execucao (staff)")
public class ExecucaoController {

    private final ListarFilaUseCase listarFila;
    private final BuscarExecucaoUseCase buscarExecucao;
    private final AvancarEtapaUseCase avancarEtapa;
    private final DefinirSimulacaoFalhaUseCase definirSimulacao;

    public ExecucaoController(ListarFilaUseCase listarFila, BuscarExecucaoUseCase buscarExecucao,
            AvancarEtapaUseCase avancarEtapa, DefinirSimulacaoFalhaUseCase definirSimulacao) {
        this.listarFila = listarFila;
        this.buscarExecucao = buscarExecucao;
        this.avancarEtapa = avancarEtapa;
        this.definirSimulacao = definirSimulacao;
    }

    @GetMapping("/fila")
    @Operation(summary = "Lista as execuções ativas, da mais antiga para a mais nova")
    public List<ExecucaoResumoResponse> fila() {
        return ExecucaoPresenter.resumos(listarFila.execute());
    }

    @GetMapping("/ordens/{osId}")
    @Operation(summary = "Detalhe da execução de uma OS")
    @ApiResponse(responseCode = "404", description = "Execução não existe")
    public ExecucaoResponse detalhe(@PathVariable UUID osId) {
        return ExecucaoPresenter.detalhe(buscarExecucao.execute(osId));
    }

    @PostMapping("/ordens/{osId}/etapa")
    @Operation(summary = "Avança a execução para a etapa de destino",
            description = "CONCLUIDA publica ExecucaoFinalizada para a saga. Repetir CONCLUIDA numa execução "
                    + "já concluída reenvia a mensagem.")
    @ApiResponse(responseCode = "404", description = "Execução não existe")
    @ApiResponse(responseCode = "409", description = "Transição inválida ou execução alterada por outra operação")
    public ExecucaoResponse avancar(@PathVariable UUID osId, @Valid @RequestBody AvancarEtapaRequest request) {
        return ExecucaoPresenter.detalhe(avancarEtapa.execute(osId, request.etapa()));
    }

    @PutMapping("/admin/simulacao-falha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Liga ou desliga a falha simulada do próximo IniciarExecucao")
    public void simulacaoFalha(@Valid @RequestBody SimulacaoFalhaRequest request) {
        definirSimulacao.execute(request.ativa());
    }
}
