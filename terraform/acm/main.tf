resource "aws_acm_certificate" "alb" {
  domain_name       = "api.corebank.cloud"
  validation_method = "DNS"

  lifecycle {
    create_before_destroy = true
  }

  tags = {
    Name      = "corebank-alb-cert"
    ManagedBy = "Terraform"
  }
}
