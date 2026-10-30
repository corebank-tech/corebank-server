variable "snapshot_identifier" {
  description = "2차 AWS 계정 소유의 복사된 RDS 스냅샷 식별자"
  type        = string
}

variable "onprem_replica_cidr" {
  description = "RDS MySQL 복제에 접근할 온프레미스 VM의 CIDR"
  type        = string
  default     = "192.168.0.211/32"
}
