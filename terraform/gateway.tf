data "aws_ssm_parameter" "api_id" {
  name = "${local.ssm_base}/gateway/api-id"
}

data "aws_ssm_parameter" "vpc_link_id" {
  name = "${local.ssm_base}/network/vpc-link-id"
}

# Integracao privada: o trafego nunca sai da VPC para chegar ao cluster.
# O stage $default (auto_deploy = true) pertence a soat-lambda-auth e
# publica estas rotas sozinho.
resource "aws_apigatewayv2_integration" "execution_service" {
  api_id             = data.aws_ssm_parameter.api_id.value
  integration_type   = "HTTP_PROXY"
  integration_method = "ANY"
  integration_uri    = aws_lb_listener.execution_service.arn
  connection_type    = "VPC_LINK"
  connection_id      = data.aws_ssm_parameter.vpc_link_id.value

  # Correlaciona a requisicao do gateway com o log da aplicacao (MDC correlationId).
  request_parameters = {
    "overwrite:header.x-correlation-id" = "$context.requestId"
    "overwrite:header.x-cliente-id"     = "$context.authorizer.clienteId"
  }
}

# Rotas publicas (sem authorizer): health e documentacao.
locals {
  rotas_publicas = [
    "GET /api/execucao/actuator/health",
    "GET /api/execucao/swagger-ui/{proxy+}",
    "GET /api/execucao/v3/api-docs/{proxy+}",
  ]
}

resource "aws_apigatewayv2_route" "publicas" {
  for_each = toset(local.rotas_publicas)

  api_id    = data.aws_ssm_parameter.api_id.value
  route_key = each.value
  target    = "integrations/${aws_apigatewayv2_integration.execution_service.id}"
}
