variable "name_prefix" {
  type    = string
  default = "corebank"
}

variable "vpc_id" {
  description = "PH-50 네트워크 모듈의 VPC ID"
  type        = string
}

variable "route_table_ids" {
  description = "온프레미스 대역으로 가는 길을 전파할 라우트 테이블 ID"
  type        = list(string)
}

variable "customer_gateway_ip" {
  description = "VM이 나가는 교육장 NAT의 공인 IP (VM에서 curl ifconfig.me로 확인)"
  type        = string
  default     = "58.230.206.76"
}

variable "onprem_cidr" {
  description = "온프레미스(VM) 대역"
  type        = string
  default     = "192.168.0.0/24"
}
