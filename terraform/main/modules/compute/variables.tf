variable "name_prefix" {
  type    = string
  default = "corebank"
}

variable "vpc_id" {
  description = "PH-50 네트워크 모듈의 VPC ID"
  type        = string
}

variable "public_subnet_ids" {
  description = "ALB가 놓일 두 AZ의 public 서브넷 ID"
  type        = list(string)
}

variable "alb_sg_id" {
  description = "PH-50 네트워크 모듈의 ALB 보안 그룹 ID"
  type        = string
}

variable "certificate_arn" {
  description = "terraform/acm이 내보낸 ALB용 ACM 인증서 ARN"
  type        = string
}
