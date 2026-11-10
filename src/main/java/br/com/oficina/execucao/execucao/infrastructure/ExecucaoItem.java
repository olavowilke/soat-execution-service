package br.com.oficina.execucao.execucao.infrastructure;

/** Nomes de atributos e chaves da tabela de execuções. */
final class ExecucaoItem {

    static final String INDICE_ETAPA = "gsi-etapa";

    static final String PK = "pk";
    static final String SK = "sk";
    static final String GSI1_PK = "gsi1pk";
    static final String GSI1_SK = "gsi1sk";

    static final String PREFIXO_EXEC = "EXEC#";
    static final String PREFIXO_ETAPA = "ETAPA#";
    static final String SK_META = "META";

    static final String CONFIG_PK = "CONFIG";
    static final String CONFIG_SK_SIMULACAO = "SIMULACAO_FALHA";
    static final String ATIVA = "ativa";

    static final String ID = "id";
    static final String OS_ID = "osId";
    static final String SAGA_ID = "sagaId";
    static final String ETAPA = "etapa";
    static final String SERVICOS = "servicos";
    static final String OBSERVACOES = "observacoes";
    static final String MOTIVO_REJEICAO = "motivoRejeicao";
    static final String HISTORICO = "historico";
    static final String CRIADA_EM = "criadaEm";
    static final String ATUALIZADA_EM = "atualizadaEm";
    static final String DESCRICAO = "descricao";
    static final String QUANTIDADE = "quantidade";
    static final String EM = "em";
    static final String VERSAO = "versao";

    private ExecucaoItem() {
    }
}
