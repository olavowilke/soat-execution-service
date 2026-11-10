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
    void estadoIlegalRetorna409() {
        var resposta = handler.handleIllegalState(new IllegalStateException("transição inválida"));

        assertThat(resposta.getStatusCode().value()).isEqualTo(409);
        assertThat(resposta.getBody().message()).isEqualTo("transição inválida");
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
