output "alb_arn" {
  value = aws_lb.main.arn
}

output "https_listener_arn" {
  value = aws_lb_listener.https.arn
}

output "alb_dns_name" {
  value = aws_lb.main.dns_name
}

output "alb_zone_id" {
  value = aws_lb.main.zone_id
}

output "target_group_arn" {
  value = aws_lb_target_group.was.arn
}

output "was_instance_profile_name" {
  value = aws_iam_instance_profile.was.name
}

output "was_role_arn" {
  value = aws_iam_role.was.arn
}

output "onprem_ssm_role_name" {
  value = aws_iam_role.onprem_ssm.name
}

output "asg_name" {
  value = aws_autoscaling_group.was.name
}
