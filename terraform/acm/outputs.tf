output "alb_certificate_arn" {
  description = "ALB HTTPS listener certificate ARN"
  value       = aws_acm_certificate.alb.arn
}

output "alb_dns_validation_records" {
  description = "DNS validation CNAME records for Gabia"

  value = [
    for dvo in aws_acm_certificate.alb.domain_validation_options : {
      name  = dvo.resource_record_name
      type  = dvo.resource_record_type
      value = dvo.resource_record_value
    }
  ]
}
