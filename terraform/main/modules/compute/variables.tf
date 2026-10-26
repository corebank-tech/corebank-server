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

variable "audit_writer_policy_arn" {
  description = "terraform/audit이 내보낸 감사 버킷 적재 정책 ARN"
  type        = string
}

variable "was_sg_id" {
  description = "PH-50 네트워크 모듈의 WAS 보안 그룹 ID"
  type        = string
}

variable "app_subnet_ids" {
  description = "WAS가 놓일 두 AZ의 app 서브넷 ID"
  type        = list(string)
}

variable "db_host" {
  description = "앱이 접속할 RDS 주소"
  type        = string
}

variable "app_image" {
  description = "서버가 받아 실행할 앱 이미지"
  type        = string
  default     = "corebank/corebank-server:latest"
}

variable "mock_external_image" {
  description = "서버가 받아 실행할 모의 대외기관 이미지"
  type        = string
  default     = "corebank/corebank-mock-external:latest"
}
