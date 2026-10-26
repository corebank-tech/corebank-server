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
  was_sg_id               = module.network.was_sg_id
  app_subnet_ids          = module.network.app_subnet_ids
  db_host                 = module.data.db_address
}

module "vpn" {
  source = "./modules/vpn"

  vpc_id          = module.network.vpc_id
  route_table_ids = concat(module.network.app_route_table_ids, [module.network.data_route_table_id])
}

module "data" {
  source = "./modules/data"

  data_subnet_ids     = module.network.data_subnet_ids
  rds_sg_id           = module.network.rds_sg_id
  snapshot_identifier = var.snapshot_identifier
}
