resource "aws_security_group" "alb" {
  name        = "${var.name_prefix}-sg-alb"
  description = "ALB - 인터넷에서 80/443만 허용"
  vpc_id      = aws_vpc.main.id

  ingress {
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${var.name_prefix}-sg-alb" }
}

resource "aws_security_group" "was" {
  name        = "${var.name_prefix}-sg-was"
  description = "WAS - ALB에서 오는 8080만 허용"
  vpc_id      = aws_vpc.main.id

  ingress {
    from_port       = 8080
    to_port         = 8080
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  egress {
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 3306
    to_port     = 3306
    protocol    = "tcp"
    cidr_blocks = [var.vpc_cidr]
  }

  egress {
    from_port   = 6379
    to_port     = 6379
    protocol    = "tcp"
    cidr_blocks = [var.vpc_cidr]
  }

  tags = { Name = "${var.name_prefix}-sg-was" }
}

resource "aws_security_group" "rds" {
  name        = "${var.name_prefix}-sg-rds"
  description = "RDS - WAS에서 오는 3306만 허용"
  vpc_id      = aws_vpc.main.id

  ingress {
    from_port       = 3306
    to_port         = 3306
    protocol        = "tcp"
    security_groups = [aws_security_group.was.id]
  }

  tags = { Name = "${var.name_prefix}-sg-rds" }
}

resource "aws_security_group" "cache" {
  name        = "${var.name_prefix}-sg-cache"
  description = "ElastiCache Redis - WAS에서 오는 6379만 허용"
  vpc_id      = aws_vpc.main.id

  ingress {
    from_port       = 6379
    to_port         = 6379
    protocol        = "tcp"
    security_groups = [aws_security_group.was.id]
  }

  tags = { Name = "${var.name_prefix}-sg-cache" }
}
