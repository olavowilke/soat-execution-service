package br.com.oficina.execucao;

import br.com.oficina.execucao.shared.LocalStackIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ContextLoadsIT extends LocalStackIT {

    @Autowired
    private ApplicationContext contexto;

    @Test
    void contextoSobe() {
        assertThat(contexto).isNotNull();
    }
}
