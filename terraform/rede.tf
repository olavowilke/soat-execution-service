data "aws_ssm_parameter" "vpc_id" {
  name = "${local.ssm_base}/network/vpc-id"
}

data "aws_ssm_parameter" "nlb_arn" {
  name = "${local.ssm_base}/network/nlb-arn"
}

# Lista separada por virgula, publicada por soat-infra-k8s.
data "aws_ssm_parameter" "node_asg_names" {
  name = "${local.ssm_base}/network/node-asg-names"
}

resource "aws_lb_target_group" "execution_service" {
  name        = "${local.prefixo}-execucao"
  port        = var.node_port
  protocol    = "TCP"
  vpc_id      = data.aws_ssm_parameter.vpc_id.value
  target_type = "instance"

  health_check {
    protocol            = "HTTP"
    path                = "/api/execucao/actuator/health"
    port                = "traffic-port"
    healthy_threshold   = 2
    unhealthy_threshold = 2
    interval            = 15
  }
}

resource "aws_lb_listener" "execution_service" {
  load_balancer_arn = data.aws_ssm_parameter.nlb_arn.value
  port              = var.listener_port
  protocol          = "TCP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.execution_service.arn
  }
}

resource "aws_autoscaling_attachment" "nodes" {
  for_each               = toset(split(",", data.aws_ssm_parameter.node_asg_names.value))
  autoscaling_group_name = each.value
  lb_target_group_arn    = aws_lb_target_group.execution_service.arn
}
