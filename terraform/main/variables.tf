variable "snapshot_identifier" {
  description = "2차 AWS 계정 소유의 복사된 RDS 스냅샷 식별자"
  type        = string
}

variable "onprem_replica_cidr" {
  description = "RDS MySQL 복제에 접근할 온프레미스 VM의 CIDR"
  type        = string
  default     = "192.168.0.211/32"
}

variable "onprem_info_api_cidr" {
  description = "WAS가 정보계 API를 부르러 나가는 온프레미스 VM의 /32"
  type        = string
  default     = "192.168.0.211/32"
}

variable "info_api_port" {
  description = "온프레미스 정보계 API가 받는 포트"
  type        = number
  default     = 8081
}

variable "info_api_base_url" {
  description = "WAS가 부를 온프레미스 정보계 API 주소. 비우면 WAS에 주입하지 않는다"
  type        = string
  default     = "http://192.168.0.211:8081"
}
