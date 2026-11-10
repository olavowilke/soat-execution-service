package br.com.oficina.execucao.execucao.infrastructure;

import br.com.oficina.execucao.execucao.gateways.SimulacaoFalhaGateway;
import br.com.oficina.execucao.shared.LocalStackIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class SimulacaoFalhaGatewayDynamoDbIT extends LocalStackIT {

    @Autowired
    private SimulacaoFalhaGateway sim;

    @Test
    void consumirComFlagDesligadaRetornaFalse() {
        sim.definir(false);

        assertThat(sim.consumir()).isFalse();
    }

    @Test
    void consumirSoRetornaTrueUmaVez() {
        sim.definir(true);

        assertThat(sim.consumir()).isTrue();
        assertThat(sim.consumir()).isFalse();
    }

    @Test
    void definirDeNovoRearmaAFlag() {
        sim.definir(true);
        sim.consumir();
        sim.definir(true);

        assertThat(sim.consumir()).isTrue();
    }

    @Test
    void consumirConcorrenteSoUmVence() throws Exception {
        int threads = 8;
        sim.definir(true);
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Boolean>> resultados = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                resultados.add(pool.submit(() -> {
                    largada.await();
                    return sim.consumir();
                }));
            }
            largada.countDown();
            long vencedores = 0;
            for (Future<Boolean> f : resultados) {
                if (f.get()) {
                    vencedores++;
                }
            }
            assertThat(vencedores).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }
}
