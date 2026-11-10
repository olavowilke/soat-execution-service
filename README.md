# Execution Service

Microsserviço da oficina que controla a fila de execução de reparos.
Guarda os dados no DynamoDB e conversa com a Saga do OS Service por SQS.

## Testes

Os testes de integração usam Testcontainers (LocalStack). Docker precisa estar ligado.

```bash
./mvnw verify
```

Plan 5 completa este README.
