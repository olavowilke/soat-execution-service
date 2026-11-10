package br.com.oficina.execucao.fixtures.camadas.usecases;

import br.com.oficina.execucao.fixtures.camadas.infrastructure.AdapterAlvo;

/** Fixture que viola a regra: use case depende de um adapter. */
public class UseCaseQueUsaInfra {
    public AdapterAlvo adapter;
}
