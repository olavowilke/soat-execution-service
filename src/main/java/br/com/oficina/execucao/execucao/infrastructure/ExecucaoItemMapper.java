package br.com.oficina.execucao.execucao.infrastructure;

import br.com.oficina.execucao.execucao.entities.EtapaExecucao;
import br.com.oficina.execucao.execucao.entities.Execucao;
import br.com.oficina.execucao.execucao.entities.HistoricoEtapa;
import br.com.oficina.execucao.execucao.entities.ServicoExecucao;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static br.com.oficina.execucao.execucao.infrastructure.ExecucaoItem.*;

final class ExecucaoItemMapper {

    private ExecucaoItemMapper() {
    }

    static String chaveEtapa(EtapaExecucao etapa) {
        return PREFIXO_ETAPA + etapa.name();
    }

    static Map<String, AttributeValue> chave(UUID osId) {
        return Map.of(PK, s(PREFIXO_EXEC + osId), SK, s(SK_META));
    }

    static Map<String, AttributeValue> paraItem(Execucao e, long versao) {
        Map<String, AttributeValue> item = new HashMap<>(chave(e.getOsId()));
        item.put(GSI1_PK, s(chaveEtapa(e.getEtapa())));
        item.put(GSI1_SK, s(e.getCriadaEm().toString()));
        item.put(VERSAO, AttributeValue.fromN(String.valueOf(versao)));
        item.put(ID, s(e.getId().toString()));
        item.put(OS_ID, s(e.getOsId().toString()));
        item.put(SAGA_ID, s(e.getSagaId().toString()));
        item.put(ETAPA, s(e.getEtapa().name()));
        item.put(SERVICOS, AttributeValue.fromL(e.getServicos().stream()
                .map(sv -> AttributeValue.fromM(Map.of(
                        DESCRICAO, s(sv.descricao()),
                        QUANTIDADE, AttributeValue.fromN(String.valueOf(sv.quantidade())))))
                .toList()));
        if (e.getCorrelationId() != null) {
            item.put(CORRELATION_ID, s(e.getCorrelationId()));
        }
        if (e.getObservacoes() != null) {
            item.put(OBSERVACOES, s(e.getObservacoes()));
        }
        if (e.getMotivoRejeicao() != null) {
            item.put(MOTIVO_REJEICAO, s(e.getMotivoRejeicao()));
        }
        item.put(HISTORICO, AttributeValue.fromL(e.getHistorico().stream()
                .map(h -> AttributeValue.fromM(Map.of(
                        ETAPA, s(h.etapa().name()),
                        EM, s(h.em().toString()))))
                .toList()));
        item.put(CRIADA_EM, s(e.getCriadaEm().toString()));
        item.put(ATUALIZADA_EM, s(e.getAtualizadaEm().toString()));
        return item;
    }

    static Execucao paraExecucao(Map<String, AttributeValue> item) {
        List<ServicoExecucao> servicos = item.get(SERVICOS).l().stream()
                .map(v -> new ServicoExecucao(
                        v.m().get(DESCRICAO).s(),
                        Integer.parseInt(v.m().get(QUANTIDADE).n())))
                .toList();
        List<HistoricoEtapa> historico = item.get(HISTORICO).l().stream()
                .map(v -> new HistoricoEtapa(
                        EtapaExecucao.valueOf(v.m().get(ETAPA).s()),
                        Instant.parse(v.m().get(EM).s())))
                .toList();
        return Execucao.reconstituir(
                UUID.fromString(item.get(ID).s()),
                UUID.fromString(item.get(OS_ID).s()),
                UUID.fromString(item.get(SAGA_ID).s()),
                EtapaExecucao.valueOf(item.get(ETAPA).s()),
                servicos,
                texto(item, OBSERVACOES),
                texto(item, MOTIVO_REJEICAO),
                texto(item, CORRELATION_ID),
                historico,
                Instant.parse(item.get(CRIADA_EM).s()),
                Instant.parse(item.get(ATUALIZADA_EM).s()),
                item.containsKey(VERSAO) ? Long.parseLong(item.get(VERSAO).n()) : 0L);
    }

    private static String texto(Map<String, AttributeValue> item, String nome) {
        AttributeValue v = item.get(nome);
        return v == null ? null : v.s();
    }

    private static AttributeValue s(String valor) {
        return AttributeValue.fromS(valor);
    }
}
