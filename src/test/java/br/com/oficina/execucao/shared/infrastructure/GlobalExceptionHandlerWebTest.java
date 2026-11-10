package br.com.oficina.execucao.shared.infrastructure;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Prova que as exceções do Spring MVC mantêm o status próprio e não geram log ERROR. */
@WebMvcTest(controllers = GlobalExceptionHandlerWebTest.ControleTeste.class)
@Import({GlobalExceptionHandler.class, GlobalExceptionHandlerWebTest.ControleTeste.class})
class GlobalExceptionHandlerWebTest {

    @RestController
    @RequestMapping("/teste")
    static class ControleTeste {
        record Corpo(@NotBlank String nome) {}

        @GetMapping("/param")
        String param(@RequestParam int numero) {
            return "ok";
        }

        @PostMapping("/corpo")
        String corpo(@Valid @RequestBody Corpo corpo) {
            return "ok";
        }

        @GetMapping("/estado")
        String estado() {
            throw new IllegalStateException("transição inválida");
        }

        @GetMapping("/boom")
        String boom() {
            throw new RuntimeException("segredo interno");
        }
    }

    @Autowired
    private MockMvc mvc;

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;
    private boolean aditivo;

    @BeforeEach
    void capturaLogs() {
        logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        aditivo = logger.isAdditive();
        logger.setAdditive(false); // erros esperados não poluem a saída
    }

    @AfterEach
    void restauraLogs() {
        logger.setAdditive(aditivo);
        logger.detachAppender(appender);
    }

    private void semLogDeErro() {
        assertThat(appender.list).noneMatch(e -> e.getLevel().isGreaterOrEqual(Level.ERROR));
    }

    @Test
    void caminhoDesconhecidoRetorna404() throws Exception {
        mvc.perform(get("/nao-existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        semLogDeErro();
    }

    @Test
    void metodoNaoPermitidoRetorna405ComAllow() throws Exception {
        mvc.perform(delete("/teste/param"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "GET"))
                .andExpect(jsonPath("$.status").value(405));
        semLogDeErro();
    }

    @Test
    void parametroAusenteRetorna400() throws Exception {
        mvc.perform(get("/teste/param"))
                .andExpect(status().isBadRequest());
        semLogDeErro();
    }

    @Test
    void parametroComTipoErradoRetorna400() throws Exception {
        mvc.perform(get("/teste/param").param("numero", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Parâmetro inválido: numero"));
        semLogDeErro();
    }

    @Test
    void corpoIlegivelRetorna400() throws Exception {
        mvc.perform(post("/teste/corpo").contentType(MediaType.APPLICATION_JSON).content("{nao-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Corpo da requisição inválido ou ilegível"));
        semLogDeErro();
    }

    @Test
    void corpoInvalidoRetorna422ComOsCampos() throws Exception {
        mvc.perform(post("/teste/corpo").contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.fields[0].field").value("nome"));
        semLogDeErro();
    }

    @Test
    void estadoIlegalRetorna409() throws Exception {
        mvc.perform(get("/teste/estado"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("transição inválida"));
        semLogDeErro();
    }

    @Test
    void erroInesperadoRetorna500ELogaErro() throws Exception {
        mvc.perform(get("/teste/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
        assertThat(appender.list).hasSize(1);
    }
}
