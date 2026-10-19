module "network" {
  source = "./modules/network"
}

module "data" {
  source = "./modules/data"

  data_subnet_ids     = module.network.data_subnet_ids
  rds_sg_id           = module.network.rds_sg_id
  snapshot_identifier = var.snapshot_identifier
}
