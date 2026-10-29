data "aws_ami" "ubuntu" {
  most_recent = true
  # Canonical
  owners = ["099720109477"]

  filter {
    name   = "name"
    values = ["ubuntu/images/hvm-ssd/ubuntu-jammy-22.04-amd64-server-*"]
  }
}

resource "aws_launch_template" "was" {
  name_prefix            = "${var.name_prefix}-was-"
  image_id               = data.aws_ami.ubuntu.id
  instance_type          = "t3.small"
  vpc_security_group_ids = [var.was_sg_id]
  update_default_version = true

  iam_instance_profile {
    name = aws_iam_instance_profile.was.name
  }

  credit_specification {
    cpu_credits = "unlimited"
  }

  metadata_options {
    http_tokens   = "required"
    http_endpoint = "enabled"
  }

  block_device_mappings {
    device_name = "/dev/sda1"

    ebs {
      volume_size = 20
      volume_type = "gp3"
      encrypted   = true
    }
  }

  user_data = base64encode(templatefile("${path.module}/user_data.sh.tftpl", {
    db_host             = var.db_host
    app_image           = var.app_image
    mock_external_image = var.mock_external_image
    interbank_host      = var.interbank_host
    info_api_base_url   = var.info_api_base_url
  }))

  tag_specifications {
    resource_type = "instance"
    tags          = { Name = "${var.name_prefix}-was" }
  }
}

resource "aws_autoscaling_group" "was" {
  name                      = "${var.name_prefix}-was-asg"
  min_size                  = 2
  max_size                  = 2
  desired_capacity          = 2
  vpc_zone_identifier       = var.app_subnet_ids
  target_group_arns         = [aws_lb_target_group.was.arn]
  health_check_type         = "ELB"
  health_check_grace_period = 1200

  launch_template {
    id      = aws_launch_template.was.id
    version = "$Latest"
  }

  tag {
    key                 = "Name"
    value               = "${var.name_prefix}-was"
    propagate_at_launch = true
  }
}
