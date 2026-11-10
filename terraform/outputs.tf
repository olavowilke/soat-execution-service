output "ecr_repository_url" {
  description = "URL do repositorio ECR do execution-service"
  value       = aws_ecr_repository.execution_service.repository_url
}

output "tabela_execucoes" {
  description = "Nome da tabela DynamoDB de execucoes"
  value       = aws_dynamodb_table.execucoes.name
}

output "fila_comandos_url" {
  description = "URL da fila de comandos"
  value       = aws_sqs_queue.comandos.url
}

output "fila_comandos_arn" {
  description = "ARN da fila de comandos"
  value       = aws_sqs_queue.comandos.arn
}

output "fila_comandos_dlq_arn" {
  description = "ARN da DLQ da fila de comandos"
  value       = aws_sqs_queue.comandos_dlq.arn
}

output "role_arn" {
  description = "ARN da role assumida pelo pod do execution-service"
  value       = aws_iam_role.execution_service.arn
}
