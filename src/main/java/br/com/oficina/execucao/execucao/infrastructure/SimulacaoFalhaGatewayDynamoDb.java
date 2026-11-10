package br.com.oficina.execucao.execucao.infrastructure;

import br.com.oficina.execucao.execucao.gateways.SimulacaoFalhaGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.util.Map;

import static br.com.oficina.execucao.execucao.infrastructure.ExecucaoItem.*;

@Component
public class SimulacaoFalhaGatewayDynamoDb implements SimulacaoFalhaGateway {

    private static final Map<String, AttributeValue> CHAVE = Map.of(
            PK, AttributeValue.fromS(CONFIG_PK),
            SK, AttributeValue.fromS(CONFIG_SK_SIMULACAO));

    private final DynamoDbClient dynamo;
    private final String tabela;

    public SimulacaoFalhaGatewayDynamoDb(DynamoDbClient dynamo,
            @Value("${execucao.dynamodb.tabela}") String tabela) {
        this.dynamo = dynamo;
        this.tabela = tabela;
    }

    @Override
    public void definir(boolean ativa) {
        dynamo.putItem(b -> b.tableName(tabela).item(Map.of(
                PK, CHAVE.get(PK),
                SK, CHAVE.get(SK),
                ATIVA, AttributeValue.fromBool(ativa))));
    }

    @Override
    public boolean consumir() {
        try {
            dynamo.updateItem(b -> b.tableName(tabela)
                    .key(CHAVE)
                    .updateExpression("SET " + ATIVA + " = :falso")
                    .conditionExpression(ATIVA + " = :verdadeiro")
                    .expressionAttributeValues(Map.of(
                            ":falso", AttributeValue.fromBool(false),
                            ":verdadeiro", AttributeValue.fromBool(true))));
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }
}
