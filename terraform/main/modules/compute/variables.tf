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
  description = "서버가 받아 실행할 앱 이미지 (컷오버에 쓰는 릴리스의 커밋 SHA 태그로 고정)"
  type        = string
  default     = "corebank/corebank-server:176fe574b6b5d2a1008ce9ce9359753b4d658cdb"
}

variable "mock_external_image" {
  description = "서버가 받아 실행할 모의 대외기관 이미지 (앱과 같은 SHA 태그로 고정)"
  type        = string
  default     = "corebank/corebank-mock-external:176fe574b6b5d2a1008ce9ce9359753b4d658cdb"
}

variable "interbank_host" {
  description = "타행 연동 모의 대외기관 주소. 비우면 서버마다 모의 서버 컨테이너를 띄우고, 값이 있으면 컨테이너를 띄우지 않고 이 주소로 접속한다"
  type        = string
  default     = ""
}
