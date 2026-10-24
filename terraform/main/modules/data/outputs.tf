output "db_instance_id" {
  description = "RDS 인스턴스 식별자"
  value       = aws_db_instance.main.identifier
}

output "db_endpoint" {
  description = "RDS 접속 엔드포인트"
  value       = aws_db_instance.main.endpoint
}

output "db_address" {
  description = "RDS 호스트명"
  value       = aws_db_instance.main.address
}

output "db_port" {
  description = "RDS 접속 포트"
  value       = aws_db_instance.main.port
}

output "db_subnet_group_name" {
  description = "RDS DB 서브넷 그룹"
  value       = aws_db_subnet_group.main.name
}
