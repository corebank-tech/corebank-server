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
