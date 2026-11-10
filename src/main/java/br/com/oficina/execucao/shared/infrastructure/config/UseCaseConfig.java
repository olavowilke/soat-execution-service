package br.com.oficina.execucao.shared.infrastructure.config;

import br.com.oficina.execucao.execucao.gateways.ExecucaoGateway;
import br.com.oficina.execucao.execucao.gateways.RespostaSagaGateway;
import br.com.oficina.execucao.execucao.gateways.SimulacaoFalhaGateway;
import br.com.oficina.execucao.execucao.usecases.IniciarExecucaoUseCase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class UseCaseConfig {

    @Bean
    public IniciarExecucaoUseCase iniciarExecucaoUseCase(ExecucaoGateway execucoes, SimulacaoFalhaGateway simulacao,
            RespostaSagaGateway respostas, Clock clock,
            @Value("${execucao.capacidade-maxima}") int capacidadeMaxima) {
        return new IniciarExecucaoUseCase(execucoes, simulacao, respostas, clock, capacidadeMaxima);
    }
}
