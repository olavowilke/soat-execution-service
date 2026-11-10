package br.com.oficina.execucao.execucao.infrastructure;

import br.com.oficina.execucao.execucao.entities.EtapaExecucao;
import br.com.oficina.execucao.execucao.entities.Execucao;
import br.com.oficina.execucao.execucao.gateways.ExecucaoGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.Select;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static br.com.oficina.execucao.execucao.infrastructure.ExecucaoItem.*;

@Component
public class ExecucaoGatewayDynamoDb implements ExecucaoGateway {

    private static final List<EtapaExecucao> ETAPAS_ATIVAS =
            List.of(EtapaExecucao.NA_FILA, EtapaExecucao.EM_REPARO, EtapaExecucao.EM_TESTE);

    private final DynamoDbClient dynamo;
    private final String tabela;

    public ExecucaoGatewayDynamoDb(DynamoDbClient dynamo, @Value("${execucao.dynamodb.tabela}") String tabela) {
        this.dynamo = dynamo;
        this.tabela = tabela;
    }

    @Override
    public boolean salvarNova(Execucao execucao) {
        try {
            dynamo.putItem(b -> b.tableName(tabela)
                    .item(ExecucaoItemMapper.paraItem(execucao))
                    .conditionExpression("attribute_not_exists(" + PK + ")"));
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }

    @Override
    public void atualizar(Execucao execucao) {
        dynamo.putItem(b -> b.tableName(tabela).item(ExecucaoItemMapper.paraItem(execucao)));
    }

    @Override
    public Optional<Execucao> buscarPorOsId(UUID osId) {
        Map<String, AttributeValue> item = dynamo.getItem(b -> b.tableName(tabela)
                .key(ExecucaoItemMapper.chave(osId))
                .consistentRead(true)).item();
        return item == null || item.isEmpty()
                ? Optional.empty()
                : Optional.of(ExecucaoItemMapper.paraExecucao(item));
    }

    @Override
    public List<Execucao> listarAtivas() {
        List<Execucao> ativas = new ArrayList<>();
        for (EtapaExecucao etapa : ETAPAS_ATIVAS) {
            consultar(etapa, false, resposta -> resposta.items()
                    .forEach(item -> ativas.add(ExecucaoItemMapper.paraExecucao(item))));
        }
        ativas.sort(Comparator.comparing(Execucao::getCriadaEm));
        return ativas;
    }

    @Override
    public long contarAtivas() {
        long[] total = {0};
        for (EtapaExecucao etapa : ETAPAS_ATIVAS) {
            consultar(etapa, true, resposta -> total[0] += resposta.count());
        }
        return total[0];
    }

    private void consultar(EtapaExecucao etapa, boolean apenasContar, Consumer<QueryResponse> aoReceber) {
        Map<String, AttributeValue> inicio = null;
        do {
            QueryRequest.Builder req = QueryRequest.builder()
                    .tableName(tabela)
                    .indexName(INDICE_ETAPA)
                    .keyConditionExpression(GSI1_PK + " = :p")
                    .expressionAttributeValues(
                            Map.of(":p", AttributeValue.fromS(ExecucaoItemMapper.chaveEtapa(etapa))))
                    .exclusiveStartKey(inicio);
            if (apenasContar) {
                req.select(Select.COUNT);
            }
            QueryResponse resposta = dynamo.query(req.build());
            aoReceber.accept(resposta);
            inicio = resposta.hasLastEvaluatedKey() ? resposta.lastEvaluatedKey() : null;
        } while (inicio != null);
    }
}
