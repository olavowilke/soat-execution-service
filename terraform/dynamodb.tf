resource "aws_dynamodb_table" "execucoes" {
  name         = "${local.prefixo}-execucoes"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "pk"
  range_key    = "sk"

  attribute {
    name = "pk"
    type = "S"
  }

  attribute {
    name = "sk"
    type = "S"
  }

  attribute {
    name = "gsi1pk"
    type = "S"
  }

  attribute {
    name = "gsi1sk"
    type = "S"
  }

  # A aplicacao le itens completos do indice, por isso projecao ALL.
  global_secondary_index {
    name            = "gsi-etapa"
    hash_key        = "gsi1pk"
    range_key       = "gsi1sk"
    projection_type = "ALL"
  }

  # Desligado para reduzir custo em homolog.
  point_in_time_recovery {
    enabled = false
  }

  # Sem bloco server_side_encryption: usa a chave padrao gerenciada pela AWS.
}
