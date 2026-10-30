data "aws_caller_identity" "current" {}

locals {
  audit_bucket_name = "corebank-audit-worm-${data.aws_caller_identity.current.account_id}"
}

resource "aws_s3_bucket" "audit" {
  bucket              = local.audit_bucket_name
  object_lock_enabled = true
  force_destroy       = true

  tags = {
    Name    = local.audit_bucket_name
    Project = "corebank"
    Purpose = "audit-worm"
  }
}

resource "aws_s3_bucket_versioning" "audit" {
  bucket = aws_s3_bucket.audit.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_object_lock_configuration" "audit" {
  bucket = aws_s3_bucket.audit.id

  rule {
    default_retention {
      mode = "GOVERNANCE"
      days = 1
    }
  }

  depends_on = [aws_s3_bucket_versioning.audit]
}

resource "aws_s3_bucket_public_access_block" "audit" {
  bucket = aws_s3_bucket.audit.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "audit" {
  bucket = aws_s3_bucket.audit.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}
