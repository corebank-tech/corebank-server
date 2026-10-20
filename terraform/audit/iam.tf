data "aws_iam_policy_document" "audit_writer" {
  statement {
    sid    = "WriteAndReadAuditObjects"
    effect = "Allow"

    actions = [
      "s3:PutObject",
      "s3:GetObject"
    ]

    resources = [
      "${aws_s3_bucket.audit.arn}/audit/*"
    ]
  }

  statement {
    sid    = "ListAuditBucket"
    effect = "Allow"

    actions = ["s3:ListBucket"]

    resources = [
      aws_s3_bucket.audit.arn
    ]
  }

  statement {
    sid    = "VerifyObjectLockVersionDeletion"
    effect = "Allow"

    actions = [
      "s3:DeleteObjectVersion"
    ]

    resources = [
      "${aws_s3_bucket.audit.arn}/audit/lock-test/*"
    ]
  }
}

resource "aws_iam_policy" "audit_writer" {
  name        = "corebank-audit-writer"
  description = "Audit object read/write without governance retention bypass"
  policy      = data.aws_iam_policy_document.audit_writer.json
}
