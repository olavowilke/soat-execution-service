package br.com.oficina.execucao.execucao.entities;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class Execucao {

    private final UUID id;
    private final UUID osId;
    private final UUID sagaId;
    private EtapaExecucao etapa;
    private final List<ServicoExecucao> servicos;
    private final String observacoes;
    private final String motivoRejeicao;
    private final List<HistoricoEtapa> historico;
    private final Instant criadaEm;
    private Instant atualizadaEm;
    private long versao;

    private Execucao(UUID id, UUID osId, UUID sagaId, EtapaExecucao etapa, List<ServicoExecucao> servicos,
            String observacoes, String motivoRejeicao, List<HistoricoEtapa> historico, Instant criadaEm,
            Instant atualizadaEm) {
        this.id = id;
        this.osId = osId;
        this.sagaId = sagaId;
        this.etapa = etapa;
        this.servicos = new ArrayList<>(servicos);
        this.observacoes = observacoes;
        this.motivoRejeicao = motivoRejeicao;
        this.historico = new ArrayList<>(historico);
        this.criadaEm = criadaEm;
        this.atualizadaEm = atualizadaEm;
    }

    public static Execucao nova(UUID osId, UUID sagaId, List<ServicoExecucao> servicos, String observacoes,
            Instant agora) {
        exigirIds(osId, sagaId);
        if (servicos == null || servicos.isEmpty()) {
            throw new IllegalArgumentException("servicos nao pode ser vazio");
        }
        return new Execucao(UUID.randomUUID(), osId, sagaId, EtapaExecucao.NA_FILA, servicos, observacoes, null,
                List.of(new HistoricoEtapa(EtapaExecucao.NA_FILA, agora)), agora, agora);
    }

    public static Execucao rejeitada(UUID osId, UUID sagaId, String motivo, Instant agora) {
        exigirIds(osId, sagaId);
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("motivo da rejeicao e obrigatorio");
        }
        return new Execucao(UUID.randomUUID(), osId, sagaId, EtapaExecucao.REJEITADA, List.of(), null, motivo,
                List.of(new HistoricoEtapa(EtapaExecucao.REJEITADA, agora)), agora, agora);
    }

    public static Execucao reconstituir(UUID id, UUID osId, UUID sagaId, EtapaExecucao etapa,
            List<ServicoExecucao> servicos, String observacoes, String motivoRejeicao,
            List<HistoricoEtapa> historico, Instant criadaEm, Instant atualizadaEm) {
        return reconstituir(id, osId, sagaId, etapa, servicos, observacoes, motivoRejeicao, historico, criadaEm,
                atualizadaEm, 0L);
    }

    public static Execucao reconstituir(UUID id, UUID osId, UUID sagaId, EtapaExecucao etapa,
            List<ServicoExecucao> servicos, String observacoes, String motivoRejeicao,
            List<HistoricoEtapa> historico, Instant criadaEm, Instant atualizadaEm, long versao) {
        Execucao e = new Execucao(id, osId, sagaId, etapa, servicos, observacoes, motivoRejeicao, historico,
                criadaEm, atualizadaEm);
        e.versao = versao;
        return e;
    }

    private static void exigirIds(UUID osId, UUID sagaId) {
        if (osId == null) {
            throw new IllegalArgumentException("osId e obrigatorio");
        }
        if (sagaId == null) {
            throw new IllegalArgumentException("sagaId e obrigatorio");
        }
    }

    public void avancarPara(EtapaExecucao destino, Instant agora) {
        if (destino == null || !etapa.proximas().contains(destino)) {
            throw new TransicaoEtapaInvalidaException(etapa, destino);
        }
        this.etapa = destino;
        this.historico.add(new HistoricoEtapa(destino, agora));
        this.atualizadaEm = agora;
    }

    /** Versão lida da persistência; usada para travamento otimista. */
    public long getVersao() {
        return versao;
    }

    /** Chamado pelo gateway depois de gravar com sucesso, para permitir novas gravações. */
    public void registrarVersaoGravada(long novaVersao) {
        this.versao = novaVersao;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOsId() {
        return osId;
    }

    public UUID getSagaId() {
        return sagaId;
    }

    public EtapaExecucao getEtapa() {
        return etapa;
    }

    public List<ServicoExecucao> getServicos() {
        return Collections.unmodifiableList(servicos);
    }

    public String getObservacoes() {
        return observacoes;
    }

    public String getMotivoRejeicao() {
        return motivoRejeicao;
    }

    public List<HistoricoEtapa> getHistorico() {
        return Collections.unmodifiableList(historico);
    }

    public Instant getCriadaEm() {
        return criadaEm;
    }

    public Instant getAtualizadaEm() {
        return atualizadaEm;
    }
}
