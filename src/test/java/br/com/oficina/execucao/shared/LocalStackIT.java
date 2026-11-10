package br.com.oficina.execucao.shared;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndex;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.Projection;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

import java.util.Map;

/**
 * Base dos testes de integração: sobe um LocalStack (SQS + DynamoDB) uma vez
 * por JVM, cria a tabela de execuções e as filas, e aponta o Spring para ele.
 */
@Testcontainers
public abstract class LocalStackIT {

    public static final String REGIAO = "us-east-1";
    public static final String TABELA = "oficina-test-execucoes";
    public static final String INDICE_ETAPA = "gsi-etapa";
    public static final String FILA_COMANDOS = "oficina-test-execution-commands";
    public static final String FILA_COMANDOS_DLQ = FILA_COMANDOS + "-dlq";
    public static final String FILA_RESPOSTAS = "oficina-test-os-saga-replies";

    private static final LocalStackContainer LOCALSTACK =
            new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.8"))
                    .withServices(LocalStackContainer.Service.SQS, LocalStackContainer.Service.DYNAMODB);

    static {
        LOCALSTACK.start();
        criaTabela();
        criaFilas();
    }

    @DynamicPropertySource
    static void propriedadesAws(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.aws.endpoint", () -> LOCALSTACK.getEndpoint().toString());
        registry.add("spring.cloud.aws.region.static", () -> REGIAO);
        registry.add("spring.cloud.aws.credentials.access-key", LOCALSTACK::getAccessKey);
        registry.add("spring.cloud.aws.credentials.secret-key", LOCALSTACK::getSecretKey);
        registry.add("execucao.dynamodb.tabela", () -> TABELA);
        // execucao.filas.comandos fica de fora: contextos em cache continuam vivos e consumiriam a fila real.
        // So o ComandosListenerIT aponta para FILA_COMANDOS; os demais usam o nome padrao do application.yml
        // (o listener cria essa fila vazia no LocalStack).
        registry.add("execucao.filas.respostas", () -> FILA_RESPOSTAS);
    }

    private static StaticCredentialsProvider credenciais() {
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(LOCALSTACK.getAccessKey(), LOCALSTACK.getSecretKey()));
    }

    private static void criaTabela() {
        try (DynamoDbClient dynamo = DynamoDbClient.builder()
                .endpointOverride(LOCALSTACK.getEndpoint())
                .region(Region.of(REGIAO))
                .credentialsProvider(credenciais())
                .build()) {
            dynamo.createTable(b -> b
                    .tableName(TABELA)
                    .billingMode(BillingMode.PAY_PER_REQUEST)
                    .attributeDefinitions(
                            atributo("pk"), atributo("sk"), atributo("gsi1pk"), atributo("gsi1sk"))
                    .keySchema(
                            chave("pk", KeyType.HASH), chave("sk", KeyType.RANGE))
                    .globalSecondaryIndexes(GlobalSecondaryIndex.builder()
                            .indexName(INDICE_ETAPA)
                            .keySchema(chave("gsi1pk", KeyType.HASH), chave("gsi1sk", KeyType.RANGE))
                            .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                            .build()));
        }
    }

    private static AttributeDefinition atributo(String nome) {
        return AttributeDefinition.builder()
                .attributeName(nome).attributeType(ScalarAttributeType.S).build();
    }

    private static KeySchemaElement chave(String nome, KeyType tipo) {
        return KeySchemaElement.builder().attributeName(nome).keyType(tipo).build();
    }

    private static void criaFilas() {
        try (SqsClient sqs = SqsClient.builder()
                .endpointOverride(LOCALSTACK.getEndpoint())
                .region(Region.of(REGIAO))
                .credentialsProvider(credenciais())
                .build()) {
            String dlqUrl = sqs.createQueue(b -> b.queueName(FILA_COMANDOS_DLQ)).queueUrl();
            String dlqArn = sqs.getQueueAttributes(b -> b
                            .queueUrl(dlqUrl).attributeNames(QueueAttributeName.QUEUE_ARN))
                    .attributes().get(QueueAttributeName.QUEUE_ARN);
            String redrive = "{\"deadLetterTargetArn\":\"" + dlqArn + "\",\"maxReceiveCount\":3}";
            sqs.createQueue(b -> b.queueName(FILA_COMANDOS).attributes(Map.of(
                    QueueAttributeName.VISIBILITY_TIMEOUT, "2",
                    QueueAttributeName.REDRIVE_POLICY, redrive)));
            sqs.createQueue(b -> b.queueName(FILA_RESPOSTAS));
        }
    }
}
