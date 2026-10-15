resource "aws_acm_certificate" "alb" {
  domain_name       = "api.corebank.cloud"
  validation_method = "DNS"

  lifecycle {
    create_before_destroy = true
    prevent_destroy       = true
  }

  tags = {
    Name      = "corebank-alb-cert"
  }
}

resource "aws_acm_certificate_validation" "alb" {
  certificate_arn = aws_acm_certificate.alb.arn

  validation_record_fqdns = [
    for dvo in aws_acm_certificate.alb.domain_validation_options :
    dvo.resource_record_name
  ]
}
