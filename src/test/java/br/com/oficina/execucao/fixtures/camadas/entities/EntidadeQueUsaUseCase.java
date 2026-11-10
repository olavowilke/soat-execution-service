package br.com.oficina.execucao.fixtures.camadas.entities;

import br.com.oficina.execucao.fixtures.camadas.usecases.UseCaseAlvo;

/** Fixture que viola a regra: entity depende de uma camada externa. */
public class EntidadeQueUsaUseCase {
    public UseCaseAlvo alvo;
}
