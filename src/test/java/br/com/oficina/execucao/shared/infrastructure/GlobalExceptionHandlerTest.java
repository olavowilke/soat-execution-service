package br.com.oficina.execucao.shared.infrastructure;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

/** Handlers chamados direto. Os casos do Spring MVC ficam em {@link GlobalExceptionHandlerWebTest}. */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void argumentoIlegalRetorna400() {
        var resposta = handler.handleIllegalArgument(new IllegalArgumentException("valor inválido"));

        assertThat(resposta.getStatusCode().value()).isEqualTo(400);
        assertThat(resposta.getBody().message()).isEqualTo("valor inválido");
    }

    @Test
    void conflitosDeDominioRetornam409() {
        var transicao = handler.handleConflito(new br.com.oficina.execucao.execucao.entities.TransicaoEtapaInvalidaException(
                br.com.oficina.execucao.execucao.entities.EtapaExecucao.NA_FILA,
                br.com.oficina.execucao.execucao.entities.EtapaExecucao.CONCLUIDA));
        var concorrente = handler.handleConflito(
                new br.com.oficina.execucao.execucao.gateways.ExecucaoAlteradaConcorrentementeException(
                        java.util.UUID.randomUUID(), null));

        assertThat(transicao.getStatusCode().value()).isEqualTo(409);
        assertThat(concorrente.getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void erroInesperadoRetorna500SemVazarDetalhes() {
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        boolean aditivo = logger.isAdditive();
        logger.setAdditive(false); // o erro esperado não polui a saída do teste
        try {
            var resposta = handler.handleGeneric(new RuntimeException("segredo interno"));

            assertThat(resposta.getStatusCode().value()).isEqualTo(500);
            assertThat(resposta.getBody().message()).doesNotContain("segredo");
            assertThat(appender.list).hasSize(1);
        } finally {
            logger.setAdditive(aditivo);
            logger.detachAppender(appender);
        }
    }
}
