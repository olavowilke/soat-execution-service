variable "project" {
  description = "Prefixo de nomes dos recursos"
  type        = string
  default     = "oficina"
}

variable "environment" {
  description = "Ambiente logico"
  type        = string

  validation {
    condition     = contains(["homolog"], var.environment)
    error_message = "environment deve ser homolog."
  }
}

variable "aws_region" {
  description = "Regiao AWS"
  type        = string
  default     = "us-east-1"
}

variable "namespace" {
  description = "Namespace Kubernetes do execution-service"
  type        = string
  default     = "execucao"
}

variable "service_account" {
  description = "ServiceAccount Kubernetes usada pelo pod do execution-service"
  type        = string
  default     = "execution-service"
}

variable "node_port" {
  description = "NodePort do Service do execution-service"
  type        = number
  default     = 30082
}

variable "listener_port" {
  description = "Porta do listener do NLB para o execution-service"
  type        = number
  default     = 8082
}
