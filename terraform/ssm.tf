# Publicados para o manifesto do Deployment, a pipeline e outras stacks
# consumirem via `data`, sem acoplar remote states (ADR-0005).
resource "aws_ssm_parameter" "fila_comandos_url" {
  name  = "${local.ssm_base}/queues/execution-commands/url"
  type  = "String"
  value = aws_sqs_queue.comandos.url
}

resource "aws_ssm_parameter" "fila_comandos_arn" {
  name  = "${local.ssm_base}/queues/execution-commands/arn"
  type  = "String"
  value = aws_sqs_queue.comandos.arn
}

resource "aws_ssm_parameter" "ecr_repository_url" {
  name  = "${local.ssm_base}/registry/execution-service/repository-url"
  type  = "String"
  value = aws_ecr_repository.execution_service.repository_url
}
