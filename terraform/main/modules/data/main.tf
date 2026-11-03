resource "aws_db_subnet_group" "main" {
  name       = "corebank-rds-subnet-group"
  subnet_ids = var.data_subnet_ids

  tags = {
    Name = "corebank-rds-subnet-group"
  }
}

resource "aws_db_parameter_group" "main" {
  name        = "corebank-mysql84-params"
  family      = "mysql8.4"
  description = "CoreBank MySQL 8.4 parameter group"

  parameter {
    name         = "time_zone"
    value        = "Asia/Seoul"
    apply_method = "immediate"
  }

  parameter {
    name         = "slow_query_log"
    value        = "1"
    apply_method = "immediate"
  }

  parameter {
    name         = "long_query_time"
    value        = "2"
    apply_method = "immediate"
  }

  parameter {
    name         = "binlog_format"
    value        = "ROW"
    apply_method = "immediate"
  }

  parameter {
    name         = "innodb_buffer_pool_size"
    value        = "{DBInstanceClassMemory*3/4}"
    apply_method = "immediate"
  }

  parameter {
    name         = "enforce_gtid_consistency"
    value        = "ON"
    apply_method = "pending-reboot"
  }

  parameter {
    name         = "gtid-mode"
    value        = "ON"
    apply_method = "pending-reboot"
  }

  parameter {
    name         = "require_secure_transport"
    value        = "1"
    apply_method = "immediate"
  }

  parameter {
    name         = "log_output"
    value        = "FILE"
    apply_method = "immediate"
  }

  tags = {
    Name = "corebank-mysql84-params"
  }
}

resource "aws_db_instance" "main" {
  identifier = "corebank-rds"

  # 기존 데이터가 담긴 스냅샷에서 복원
  snapshot_identifier = var.snapshot_identifier
  engine              = "mysql"

  # PH-57 용량 산정 결과
  instance_class        = "db.t3.small"
  allocated_storage     = 20
  storage_type          = "gp3"
  max_allocated_storage = 0
  multi_az              = true

  # PH-50 네트워크와 연결
  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [var.rds_sg_id]
  publicly_accessible    = false

  # 신규 MySQL 8.4 파라미터 그룹
  parameter_group_name = aws_db_parameter_group.main.name

  # 백업 및 데이터 보호
  storage_encrypted         = true
  backup_retention_period   = 7
  deletion_protection       = true
  skip_final_snapshot       = false
  final_snapshot_identifier = "corebank-rds-final"

  engine_version             = "8.4.10"
  auto_minor_version_upgrade = false

  # AWS 설정 시간은 UTC 기준
  backup_window      = "18:00-18:30"
  maintenance_window = "Sun:19:00-Sun:19:30"

  enabled_cloudwatch_logs_exports = ["error", "slowquery"]

  tags = {
    Name = "corebank-rds"
  }

  lifecycle {
    ignore_changes = [snapshot_identifier]
  }
}
