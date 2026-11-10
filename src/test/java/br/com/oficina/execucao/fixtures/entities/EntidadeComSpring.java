package br.com.oficina.execucao.fixtures.entities;

import org.springframework.http.HttpStatus;

/** Fixture que viola a regra: entity depende de Spring. */
public class EntidadeComSpring {
    public HttpStatus status;
}
