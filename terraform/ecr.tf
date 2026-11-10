resource "aws_ecr_repository" "execution_service" {
  name                 = "${local.prefixo}-${local.nome_servico}"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_ecr_lifecycle_policy" "execution_service" {
  repository = aws_ecr_repository.execution_service.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Mantem as 10 imagens mais recentes"
        selection = {
          tagStatus   = "any"
          countType   = "imageCountMoreThan"
          countNumber = 10
        }
        action = {
          type = "expire"
        }
      }
    ]
  })
}
