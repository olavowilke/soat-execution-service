package br.com.oficina.execucao.shared.infrastructure;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void validacaoRetorna422ComOsCampos() throws Exception {
        var resultado = new BeanPropertyBindingResult(new Object(), "corpo");
        resultado.addError(new FieldError("corpo", "descricao", "não pode ser vazio"));
        MethodParameter parametro = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("validacaoRetorna422ComOsCampos"), -1);

        var resposta = handler.handleValidation(new MethodArgumentNotValidException(parametro, resultado));

        assertThat(resposta.getStatusCode().value()).isEqualTo(422);
        assertThat(resposta.getBody().fields())
                .containsExactly(new ApiError.FieldError("descricao", "não pode ser vazio"));
    }

    @Test
    void corpoIlegivelRetorna400() {
        var excecao = new HttpMessageNotReadableException("json ruim", (HttpInputMessage) null);

        var resposta = handler.handleMessageNotReadable(excecao);

        assertThat(resposta.getStatusCode().value()).isEqualTo(400);
        assertThat(resposta.getBody().message()).contains("inválido");
    }

    @Test
    void parametroComTipoErradoRetorna400() {
        var excecao = new MethodArgumentTypeMismatchException("abc", Integer.class, "id", null, null);

        var resposta = handler.handleTypeMismatch(excecao);

        assertThat(resposta.getStatusCode().value()).isEqualTo(400);
        assertThat(resposta.getBody().message()).isEqualTo("Parâmetro inválido: id");
    }

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
