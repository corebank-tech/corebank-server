output "tunnel1_address" {
  value = aws_vpn_connection.onprem.tunnel1_address
}

output "tunnel1_cgw_inside_address" {
  value = aws_vpn_connection.onprem.tunnel1_cgw_inside_address
}

output "tunnel1_vgw_inside_address" {
  value = aws_vpn_connection.onprem.tunnel1_vgw_inside_address
}

output "tunnel1_preshared_key" {
  value     = aws_vpn_connection.onprem.tunnel1_preshared_key
  sensitive = true
}
