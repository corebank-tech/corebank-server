output "audit_bucket_name" {
  value = aws_s3_bucket.audit.id
}

output "audit_bucket_arn" {
  value = aws_s3_bucket.audit.arn
}

output "audit_writer_policy_arn" {
  value = aws_iam_policy.audit_writer.arn
}
