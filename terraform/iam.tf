data "aws_ssm_parameter" "cluster_name" {
  name = "${local.ssm_base}/cluster/name"
}

# Pod Identity: o servico pods.eks.amazonaws.com assume a role em nome do pod.
data "aws_iam_policy_document" "confianca" {
  statement {
    actions = ["sts:AssumeRole", "sts:TagSession"]

    principals {
      type        = "Service"
      identifiers = ["pods.eks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "execution_service" {
  name               = "${local.prefixo}-${local.nome_servico}"
  assume_role_policy = data.aws_iam_policy_document.confianca.json
}

data "aws_iam_policy_document" "permissoes" {
  statement {
    sid = "TabelaExecucoes"
    actions = [
      "dynamodb:GetItem",
      "dynamodb:PutItem",
      "dynamodb:UpdateItem",
      "dynamodb:Query",
    ]
    resources = [
      aws_dynamodb_table.execucoes.arn,
      "${aws_dynamodb_table.execucoes.arn}/index/*",
    ]
  }

  statement {
    sid = "ConsumirComandos"
    actions = [
      "sqs:ReceiveMessage",
      "sqs:DeleteMessage",
      "sqs:ChangeMessageVisibility",
      "sqs:GetQueueAttributes",
      "sqs:GetQueueUrl",
    ]
    resources = [aws_sqs_queue.comandos.arn]
  }

  statement {
    sid = "EnviarRespostas"
    actions = [
      "sqs:SendMessage",
      "sqs:GetQueueUrl",
      "sqs:GetQueueAttributes",
    ]
    resources = [local.fila_respostas_arn]
  }
}

resource "aws_iam_role_policy" "permissoes" {
  name   = "execution-service"
  role   = aws_iam_role.execution_service.id
  policy = data.aws_iam_policy_document.permissoes.json
}

resource "aws_eks_pod_identity_association" "execution_service" {
  cluster_name    = data.aws_ssm_parameter.cluster_name.value
  namespace       = var.namespace
  service_account = var.service_account
  role_arn        = aws_iam_role.execution_service.arn
}
