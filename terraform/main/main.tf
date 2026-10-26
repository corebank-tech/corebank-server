module "network" {
  source = "./modules/network"
}

data "terraform_remote_state" "acm" {
  backend = "s3"

  config = {
    bucket = "corebank-terraform-state-853900964665"
    key    = "acm/terraform.tfstate"
    region = "ap-northeast-2"
  }
}

data "terraform_remote_state" "audit" {
  backend = "s3"

  config = {
    bucket = "corebank-terraform-state-853900964665"
    key    = "audit/terraform.tfstate"
    region = "ap-northeast-2"
  }
}

module "compute" {
  source = "./modules/compute"

  vpc_id                  = module.network.vpc_id
  public_subnet_ids       = module.network.public_subnet_ids
  alb_sg_id               = module.network.alb_sg_id
  certificate_arn         = data.terraform_remote_state.acm.outputs.alb_certificate_arn
  audit_writer_policy_arn = data.terraform_remote_state.audit.outputs.audit_writer_policy_arn
}

module "data" {
  source = "./modules/data"

  data_subnet_ids     = module.network.data_subnet_ids
  rds_sg_id           = module.network.rds_sg_id
  snapshot_identifier = var.snapshot_identifier
}
