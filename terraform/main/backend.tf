terraform {
  backend "s3" {
    bucket       = "corebank-terraform-state-853900964665"
    key          = "network/terraform.tfstate"
    region       = "ap-northeast-2"
    use_lockfile = true
  }
}