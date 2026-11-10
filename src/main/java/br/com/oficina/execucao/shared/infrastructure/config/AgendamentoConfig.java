package br.com.oficina.execucao.shared.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Liga o agendador usado por MetricasFilas. */
@Configuration
@EnableScheduling
public class AgendamentoConfig {
}
