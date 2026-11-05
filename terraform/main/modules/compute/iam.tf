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

# AmazonSSMManagedInstanceCore에서 ssm:GetParameter(s)만 뺀 자체 정책. 관리형 정책은 Parameter Store 전체 읽기를 허용한다(#775).
data "aws_iam_policy_document" "onprem_ssm_agent" {
  statement {
    actions = [
      "ssm:DescribeAssociation",
      "ssm:DescribeDocument",
      "ssm:GetDeployablePatchSnapshotForInstance",
      "ssm:GetDocument",
      "ssm:GetManifest",
      "ssm:ListAssociations",
      "ssm:ListInstanceAssociations",
      "ssm:PutComplianceItems",
      "ssm:PutConfigurePackageResult",
      "ssm:PutInventory",
      "ssm:UpdateAssociationStatus",
      "ssm:UpdateInstanceAssociationStatus",
      "ssm:UpdateInstanceInformation",
      "ssmmessages:CreateControlChannel",
      "ssmmessages:CreateDataChannel",
      "ssmmessages:OpenControlChannel",
      "ssmmessages:OpenDataChannel",
      "ec2messages:AcknowledgeMessage",
      "ec2messages:DeleteMessage",
      "ec2messages:FailMessage",
      "ec2messages:GetEndpoint",
      "ec2messages:GetMessages",
      "ec2messages:SendReply",
    ]
    resources = ["*"]
  }
}

resource "aws_iam_policy" "onprem_ssm_agent" {
  name   = "${var.name_prefix}-onprem-ssm-agent"
  policy = data.aws_iam_policy_document.onprem_ssm_agent.json
}

resource "aws_iam_role_policy_attachment" "onprem_ssm" {
  role       = aws_iam_role.onprem_ssm.name
  policy_arn = aws_iam_policy.onprem_ssm_agent.arn
}
