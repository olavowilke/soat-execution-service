package br.com.oficina.execucao.execucao.gateways;

import br.com.oficina.execucao.execucao.entities.Execucao;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExecucaoGateway {

    /** Retorna {@code false} se já existe uma execução para o {@code osId}. */
    boolean salvarNova(Execucao execucao);

    /** Lança ExecucaoAlteradaConcorrentementeException se o item não existe ou a versão lida ficou velha. */
    void atualizar(Execucao execucao);

    Optional<Execucao> buscarPorOsId(UUID osId);

    /** Execuções em NA_FILA, EM_REPARO e EM_TESTE, da mais antiga para a mais nova. */
    List<Execucao> listarAtivas();

    long contarAtivas();
}
