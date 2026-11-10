package br.com.oficina.execucao.execucao.gateways;

import java.util.UUID;

/** A execução não existe ou mudou desde que foi lida. */
public class ExecucaoAlteradaConcorrentementeException extends RuntimeException {

    public ExecucaoAlteradaConcorrentementeException(UUID osId, Throwable causa) {
        super("Execucao da OS " + osId + " inexistente ou alterada por outra operacao", causa);
    }
}
