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

output "alb_arn" {
  value = module.compute.alb_arn
}

output "https_listener_arn" {
  value = module.compute.https_listener_arn
}

output "alb_dns_name" {
  value = module.compute.alb_dns_name
}

output "alb_zone_id" {
  value = module.compute.alb_zone_id
}

output "target_group_arn" {
  value = module.compute.target_group_arn
}

output "rds_instance_id" {
  value = module.data.db_instance_id
}

output "rds_endpoint" {
  value = module.data.db_endpoint
}

output "rds_address" {
  value = module.data.db_address
}

output "rds_port" {
  value = module.data.db_port
}
