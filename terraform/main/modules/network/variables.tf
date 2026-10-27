variable "vpc_cidr" {
  default = "10.30.0.0/16"
}

variable "azs" {
  default = ["ap-northeast-2a", "ap-northeast-2c"]
}

variable "public_subnet_cidrs" {
  default = ["10.30.0.0/24", "10.30.1.0/24"]
}

variable "app_subnet_cidrs" {
  default = ["10.30.10.0/24", "10.30.11.0/24"]
}

variable "data_subnet_cidrs" {
  default = ["10.30.20.0/24", "10.30.21.0/24"]
}

variable "name_prefix" {
  default = "corebank"
}

variable "onprem_metrics_cidr" {
  description = "VPN으로 WAS 8080(메트릭)에 닿는 온프레미스 VM의 /32. 다른 VM이 필요해지면 여기를 바꾼다"
  default     = "192.168.0.211/32"
}
