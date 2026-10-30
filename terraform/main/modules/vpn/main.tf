resource "aws_customer_gateway" "onprem" {
  bgp_asn    = 65000
  ip_address = var.customer_gateway_ip
  type       = "ipsec.1"

  tags = { Name = "${var.name_prefix}-cgw-onprem" }
}

resource "aws_vpn_gateway" "main" {
  vpc_id = var.vpc_id

  tags = { Name = "${var.name_prefix}-vgw" }
}

resource "aws_vpn_connection" "onprem" {
  customer_gateway_id = aws_customer_gateway.onprem.id
  vpn_gateway_id      = aws_vpn_gateway.main.id
  type                = "ipsec.1"
  static_routes_only  = true

  tags = { Name = "${var.name_prefix}-vpn-onprem" }
}

resource "aws_vpn_connection_route" "onprem" {
  vpn_connection_id      = aws_vpn_connection.onprem.id
  destination_cidr_block = var.onprem_cidr
}

resource "aws_vpn_gateway_route_propagation" "main" {
  count          = length(var.route_table_ids)
  vpn_gateway_id = aws_vpn_gateway.main.id
  route_table_id = var.route_table_ids[count.index]
}
