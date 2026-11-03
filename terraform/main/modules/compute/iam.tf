data "aws_caller_identity" "current" {}
data "aws_region" "current" {}

data "aws_iam_policy_document" "ec2_assume" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "was" {
  name               = "${var.name_prefix}-was-role"
  assume_role_policy = data.aws_iam_policy_document.ec2_assume.json
}

resource "aws_iam_role_policy_attachment" "was_ssm" {
  role       = aws_iam_role.was.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_role_policy_attachment" "was_audit" {
  role       = aws_iam_role.was.name
  policy_arn = var.audit_writer_policy_arn
}

data "aws_iam_policy_document" "was_parameters" {
  statement {
    actions   = ["ssm:GetParameter", "ssm:GetParameters", "ssm:GetParametersByPath"]
    resources = ["arn:aws:ssm:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:parameter/corebank/*"]
  }
}

resource "aws_iam_role_policy" "was_parameters" {
  name   = "${var.name_prefix}-was-parameters"
  role   = aws_iam_role.was.id
  policy = data.aws_iam_policy_document.was_parameters.json
}

resource "aws_iam_instance_profile" "was" {
  name = "${var.name_prefix}-was-profile"
  role = aws_iam_role.was.name
}

data "aws_iam_policy_document" "ssm_assume" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ssm.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "onprem_ssm" {
  name               = "${var.name_prefix}-onprem-ssm-role"
  assume_role_policy = data.aws_iam_policy_document.ssm_assume.json
}

resource "aws_iam_role_policy_attachment" "onprem_ssm" {
  role       = aws_iam_role.onprem_ssm.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}
