variable "data_subnet_ids" {
  description = "RDS가 사용할 두 AZ의 데이터 서브넷 ID"
  type        = list(string)
}

variable "rds_sg_id" {
  description = "PH-50 네트워크 모듈에서 생성하는 RDS 보안 그룹 ID"
  type        = string
}

variable "snapshot_identifier" {
  description = "2차 AWS 계정으로 복사한 RDS 스냅샷 식별자"
  type        = string

  validation {
    condition     = length(trimspace(var.snapshot_identifier)) > 0
    error_message = "RDS 스냅샷 식별자를 입력해야 합니다."
  }
}
