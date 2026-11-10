package br.com.oficina.execucao.fixtures.usecases;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Fixture que viola a regra: use case depende de Jackson. */
public class UseCaseComJackson {
    public ObjectMapper mapper;
}
