output "vpc_id" {
  value = module.network.vpc_id
}

output "public_subnet_ids" {
  value = module.network.public_subnet_ids
}

output "app_subnet_ids" {
  value = module.network.app_subnet_ids
}

output "data_subnet_ids" {
  value = module.network.data_subnet_ids
}

output "alb_sg_id" {
  value = module.network.alb_sg_id
}

output "was_sg_id" {
  value = module.network.was_sg_id
}

output "rds_sg_id" {
  value = module.network.rds_sg_id
}

output "cache_sg_id" {
  value = module.network.cache_sg_id
}
