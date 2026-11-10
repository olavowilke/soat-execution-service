package br.com.oficina.execucao;

import br.com.oficina.execucao.shared.LocalStackIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ContextLoadsIT extends LocalStackIT {

    @Autowired
    private ApplicationContext contexto;

    // O TestRestTemplate ja prefixa o context-path (/api/execucao).
    @Autowired
    private TestRestTemplate http;

    @Test
    void sondaDeProntidaoResponde200() {
        var resposta = http.getForEntity("/actuator/health/readiness", String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void sondaDeVivacidadeResponde200() {
        var resposta = http.getForEntity("/actuator/health/liveness", String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void contextoSobe() {
        assertThat(contexto).isNotNull();
    }
}
