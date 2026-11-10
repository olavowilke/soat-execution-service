package br.com.oficina.execucao.shared.infrastructure;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Execution Service")
                        .description("Fila de execução de reparos da oficina. Integra com o OS Service por SQS.")
                        .version("1.0.0"))
                .servers(List.of(new Server().url("/api/execucao").description("Execution Service")));
    }
}
