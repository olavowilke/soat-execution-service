package br.com.oficina.execucao.fixtures.gateways;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/** Fixture que viola a regra: gateway (port) depende do AWS SDK. */
public class GatewayComAws {
    public DynamoDbClient dynamo;
}
