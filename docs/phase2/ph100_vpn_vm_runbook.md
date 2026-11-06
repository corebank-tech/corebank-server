# PH-100-② VPN — VM(온프레미스) 쪽 설정 런북

> 새 계정 VPC(10.30.0.0/16)와 부트캠프 VM(192.168.0.0/24)을 Site-to-Site VPN으로 잇는 중 **VM 쪽** 절차를 적는다(#753). AWS 쪽은 `terraform/main/modules/vpn`이다.
> 값은 `<...>` 자리표시자로 둔다. **공유 비밀키(PSK)와 터널 주소는 이 문서·저장소·채팅에 적지 않는다.**
> 10/19에 일회용 AWS 환경(임시 VPC 10.99.0.0/16)과 테스트 VM으로 같은 구조를 시험해 터널이 서고 양방향 통신이 된 것을 확인했다. 아래 설정은 그 시험에서 나온 것이다.

## 0. 전제와 아직 확인되지 않은 것

- 방식: **VM이 먼저 터널을 여는 아웃바운드 방식**이다. VM은 NAT 뒤(192.168.0.211)라서 공유기 포트포워딩은 필요 없다(10/19 시험).
- 터널은 **터널 1만** 설정한다. AWS가 터널을 2개 만들지만 터널 2는 DOWN이어도 정상이다.
- **터널 2는 하지 않기로 했다(#817, 2026-11-06).** 이유: 3-Tier 철거(11/13)까지 남은 기간이 짧고 시연(11/18)은 VM 레플리카 승격으로 해서 AWS 쪽 터널이 필요 없어진다. 터널 1 옆에 경로 우선순위를 건드리면 지금 동작하는 터널 1과 복제에 영향을 줄 위험이 있다. 11/3 장애(교육장 공인 IP 변경)는 두 터널이 같은 고객 게이트웨이를 쓰면 터널 2로도 막지 못한다(이 환경의 설정은 확인하지 않았다). 남는 위험은 터널 1이 끊기면 복제 · 정보계 조회 · 메트릭 수집이 멈추는 것이고, 복제는 binlog 보관(72시간) 안에 복구되면 이어서 따라간다. 복구 절차는 §5-1.
- Customer Gateway에 등록된 공인 IP는 교육장 NAT 출구 주소다(값은 문서에 쓰지 않는다). 2026-11-03에 이 주소가 바뀌어 고객 게이트웨이를 교체했다(§5-1, #851).
- **최종 VM에서 확인함(2026-10-28~29)**: UDP 500/4500 통과, 터널 성립, 장시간 유지, VM → WAS 8080, VM → RDS 3306, 재부팅 후 자동 복구.
- **최종 VM에서 아직 확인하지 못한 것**: WAS → VM(정보계 API·모의 대외기관). 터널 2 장애 전환은 하지 않기로 했다(위).
- **VM은 우리 팀 전용으로 본다**(팀마다 IP가 달라 사용자가 확인, 2026-10-26). 접속자와 `/home`도 `student` 하나뿐이었다. 그래도 PSK는 root만 읽는 파일(권한 600)에만 두고 화면·채팅·저장소에 남기지 않는다.
- **이 VM에는 #691(정보계 ETL)이 이미 둔 것이 있다.** `/etc/info-etl/`(비밀 파일), 마트 MySQL 컨테이너 `info-mart-mysql`(`127.0.0.1:3307`), `/var/lib/info-mart`, 호스트 포트 3306(레플리카 자리)·3307·80(Nginx 자리). VPN 작업은 이것들을 건드리지 않는다.
- **VM 명령은 한 번에 한 세션만** 입력한다. `apt`는 동시에 돌 수 없고, 도커·네트워크 서비스를 다시 시작하면 마트가 영향을 받는다.

## 1. 먼저 얻을 것 (AWS 쪽 apply 뒤)

루트 `terraform output`에서 가져온다. PSK는 화면에 출력하지 않는다.

- `vpn_tunnel1_address`: AWS 터널 공인 주소 → 아래 `<AWS 터널 공인 주소>`
- `vpn_tunnel1_preshared_key`(sensitive): PSK. `terraform output -raw vpn_tunnel1_preshared_key | pbcopy`로 클립보드에 두고 붙여 넣는다.
- `vpn_tunnel1_cgw_inside_address`, `vpn_tunnel1_vgw_inside_address`: 터널 내부 주소(169.254.x.x). 시험에서는 쓰지 않았고 참고용이다.

## 2. 터널을 올리기 전 안전장치 (SSH 끊김 방지)

설정이 틀리면 VM의 모든 트래픽이 터널로 들어가 **SSH가 끊길 수 있다.** 10/19 시험에서 실제로 겪었다.

1. 자동 정지를 건다. 잠겨도 30분 뒤 스스로 복구된다.
   ```bash
   sudo systemd-run --on-active=30m --unit=vpn-auto-stop /usr/sbin/ipsec stop
   ```
2. `strongswan-starter`의 부팅 자동 시작을 끈다(재부팅 시 잠김 방지). 초기 설정 동안만 끄고, 터널이 안정된 뒤 §3-7에서 다시 켠다.
   ```bash
   sudo systemctl disable --now strongswan-starter
   ```
3. 이 VM에 **다른 접속 경로**(콘솔, 강사님 연락)가 있는지 확인한다.

## 3. 설치와 설정

### 3-1. 패키지
```bash
sudo apt-get update && sudo apt-get install -y strongswan
```
**완료(2026-10-26):** strongSwan 5.9.5가 설치됐고 `strongswan-starter`는 `disable --now`로 꺼 두었다(설정 없이 켜져 있지 않게). 설치 중 needrestart 화면은 서비스 재시작을 모두 해제했다(docker·ssh·networkd 재시작 방지, 마트 컨테이너 보호). 설치 전후 `ip route`의 기본 경로(`default via 192.168.0.1 dev enp6s18`)는 그대로였다.

### 3-2. `/etc/strongswan.d/zz-vpn.conf` (charon 설정)
```
charon { install_routes = no }
```
AWS 정적 라우팅의 트래픽 선택기가 `0.0.0.0/0`이라서, 이 설정이 없으면 VM의 기본 경로가 터널로 들어간다.

**완료(2026-10-26):** 이 파일을 만들었다. `/etc/ipsec.conf`·`/etc/ipsec.secrets`와 VTI는 AWS 쪽 apply로 터널 주소와 PSK가 나온 뒤에 만든다(§3-3 이후).

### 3-3. `/etc/ipsec.conf` (연결 설정)
```
conn aws-t1
    keyexchange=ikev2
    type=tunnel
    auto=start
    authby=secret
    left=%defaultroute
    leftid=<VM 공인 IP>
    leftsubnet=0.0.0.0/0
    right=<AWS 터널 공인 주소>
    rightsubnet=0.0.0.0/0
    ike=aes128-sha256-modp2048!
    esp=aes128-sha256-modp2048!
    ikelifetime=8h
    lifetime=1h
    keyingtries=%forever
    dpddelay=10s
    dpdtimeout=30s
    dpdaction=restart
    mark=100
    forceencaps=yes
```
- `leftid`는 내부 IP(192.168.0.211)가 아니라 **공인 IP**여야 한다. 교육장 공인 IP가 바뀌어도 옛 값이 남은 채로 붙은 사례가 있다(2026-11-03, §5-1).
- `mark=100`이 빠지면 모든 트래픽이 터널로 들어간다.
- `if_id_in` 같은 XFRM 키워드는 strongSwan 5.9.5의 `ipsec.conf`가 모른다(`unknown keyword`). 쓰지 않는다.
- **`auto=start`는 strongSwan이 시작될 때만 연결하고, `dpdaction=restart`는 상대가 응답하지 않을 때만 다시 붙인다.** AWS가 정상적으로 세션 삭제(DELETE)를 보내면 둘 다 작동하지 않아 VM이 재연결하지 않는다(2026-11-01 23:18 실제 사례, #851). `closeaction=restart`로 막을 수 있는지는 복구 뒤 검증할 예정이며, 검증 전에는 이 런북의 설정에 넣지 않았다.

### 3-4. `/etc/ipsec.secrets`
```
<VM 공인 IP> <AWS 터널 공인 주소> : PSK "<PSK>"
```
```bash
sudo chmod 600 /etc/ipsec.secrets
```
PSK는 화면에 찍지 않고 편집기에 붙여 넣는다. 파일은 root만 읽을 수 있게 둔다.

### 3-5. VTI 인터페이스와 경로 (터널이 올라간 뒤)
```bash
sudo ip tunnel add vti1 local <VM 내부 IP> remote <AWS 터널 공인 주소> mode vti key 100
sudo ip link set vti1 up mtu 1400
sudo sysctl -w net.ipv4.conf.vti1.disable_policy=1
sudo sysctl -w net.ipv4.conf.vti1.rp_filter=2
sudo ip route add 10.30.0.0/16 dev vti1 src <VM 내부 IP>
```
- `mark=100`만으로는 터널은 서지만 패킷이 `vti1`로 오가지 못한다. VTI와 `10.30.0.0/16` 경로가 있어야 양방향 통신이 된다.
- `install_routes = no`라서 이 경로는 직접 넣는다. 이것이 VM의 기본 경로를 보호한다.
- 재부팅하면 `vti1`과 경로가 사라진다. 지속시키는 방법은 §3-7이다.

### 3-6. 시작
```bash
sudo ipsec restart
sudo ipsec statusall
```

### 3-7. 재부팅 후 자동 복구 (터널이 안정된 뒤)

`/usr/local/sbin/vpn-vti-up.sh` (`chmod 755`). 터널 주소는 파일에 적지 않고 `/etc/ipsec.conf`의 `right=`에서 읽는다. 여러 번 실행해도 안전하다.
```sh
#!/bin/sh
set -e
T=$(awk -F= '/^[[:space:]]*right=/{print $2; exit}' /etc/ipsec.conf)
ip link show vti1 >/dev/null 2>&1 || ip tunnel add vti1 local <VM 내부 IP> remote "$T" mode vti key 100
ip link set vti1 up mtu 1400
sysctl -w net.ipv4.conf.vti1.disable_policy=1
sysctl -w net.ipv4.conf.vti1.rp_filter=2
ip route replace 10.30.0.0/16 dev vti1 src <VM 내부 IP>
```
`/etc/systemd/system/vpn-vti.service`
```ini
[Unit]
Description=VPN VTI and route
After=strongswan-starter.service network-online.target
Wants=network-online.target

[Service]
Type=oneshot
RemainAfterExit=yes
ExecStart=/usr/local/sbin/vpn-vti-up.sh

[Install]
WantedBy=multi-user.target
```
```bash
sudo chmod 755 /usr/local/sbin/vpn-vti-up.sh
sudo systemctl daemon-reload
sudo systemctl start vpn-vti
sudo systemctl enable strongswan-starter
sudo systemctl enable vpn-vti
```
- `start`는 이미 서 있는 터널에 영향이 없다. 경로가 `dev vti1`이고 RDS 3306이 그대로 닿는지 확인한 뒤 `enable`한다.
- 실행 권한이 없으면 `ExecStart`가 `Permission denied`로 실패해 부팅 때 VTI와 경로가 복구되지 않는다. 위 `chmod 755`를 빠뜨리지 않는다.
- VPN 연결을 새로 만들어 AWS 터널 주소가 바뀌면 기존 `vti1`이 낡은 주소로 남는다(스크립트는 `vti1`이 있으면 새로 만들지 않는다). `/etc/ipsec.conf`의 `right=`를 고친 뒤 `sudo ip tunnel del vti1`을 하고 `sudo systemctl restart vpn-vti`로 다시 만든다.

**완료(2026-10-29):** 위 순서로 적용했다. 마트·레플리카 컨테이너(`info-mart-mysql`, `info-replica-mysql`)는 `unless-stopped`라 재부팅 후 같이 올라온다.

## 4. 확인

### 4-1. 터널 기본 검증 (§2의 타이머가 걸린 채로, 30분 안에)

1. 터널 상태: `sudo ipsec statusall`에 `ESTABLISHED`, `INSTALLED, TUNNEL`이 보인다. AWS 콘솔(VPC > Site-to-Site VPN 연결)에서 터널 1이 UP이고 터널 2는 DOWN이어도 정상이다.
2. VM 기본 경로가 그대로인지: `ip route`에서 `default via ...`가 터널이 아닌 원래 경로다. SSH가 유지되는지 같이 본다.
3. VTI와 경로: `ip -br link show vti1`이 UP이고, `ip route | grep 10.30.0.0/16`에 `dev vti1` 경로가 보인다. WAS 같은 서비스 포트는 여기서 확인하지 않는다. 터널이 정상이어도 WAS가 아직 기동 중일 수 있고(ASG 헬스체크 유예가 1200초) 그 때문에 이 단계가 막히면 30분 타이머가 먼저 실행된다.
4. 복구 확인: VM에서 `sudo ipsec restart`로 다시 올려 SPI가 바뀌며 복구되는지 본다.

### 4-2. 자동 정지 취소 (필수)

4-1의 1~4가 통과하면 곧바로 타이머를 취소하고 사라졌는지 확인한다. 타이머가 남아 있으면 정상 연결도 30분 뒤 `ipsec stop`으로 끊겨 RDS 복제와 정보계 통신이 멈춘다. P2의 RDS 보안 그룹 변경이나 VM 서비스 포트 확정을 기다리는 것은 취소를 미룰 이유가 아니다(그 검증은 4-3에서 따로 한다).
```bash
sudo systemctl stop vpn-auto-stop.timer
systemctl list-timers --all | grep vpn-auto-stop || echo "타이머 없음(취소됨)"
```
- 4-1이 30분 안에 끝나지 않으면 먼저 위 `stop`으로 기존 타이머를 지운 뒤 §2의 `systemd-run`을 다시 건다. 같은 이름(`vpn-auto-stop`)의 타이머가 살아 있는 채로 다시 걸면 실패한다.
- 이 단계 전은 초기 설정 단계(안전장치 있음), 이후는 정상 운영 단계(안전장치 없음)다. 이후 SSH가 끊기면 §5의 콘솔 접속을 쓴다.

### 4-3. 서비스 포트 연동 검증 (타이머 취소 뒤, 선행 조건이 갖춰진 때)

PH-100-② 완료 기준이다. 상대 쪽 준비가 끝난 항목부터 따로 확인한다.
- VM → WAS 8080 (메트릭 수집, 이 PR의 WAS 보안 그룹 규칙): **완료(2026-10-28)** 두 서버 모두 `status UP`. WAS가 기동한 뒤에 확인한다. 먼저 `command -v nc`로 `nc`가 있는지 확인하고, 없으면 `sudo apt install netcat-openbsd`로 설치한 다음 `nc -vz <WAS 사설 IP> 8080`(TCP 연결 검사)을 실행한다. WAS 보안 그룹은 ICMP를 허용하지 않고 RDS도 ping에 응답하지 않아서 `ping`은 터널이 정상이어도 실패한다.
- VM → RDS 3306: 복제 계정은 P2 PH-70-②에서 정한다. 보안 그룹의 RDS 규칙은 P2가 추가한 뒤 `nc -vz <RDS 사설 IP> 3306`으로 확인한다. **완료(2026-10-29):** `nc -vz -w 5 <RDS 엔드포인트> 3306`이 `succeeded`였고 이름이 사설 IP로 풀려 터널을 탔다. 이 규칙의 Terraform 반영은 PR #794다.
- WAS → VM의 정보계 API: **완료(2026-11-03, #823).** 릴리스 3 반영으로 WAS 보안 그룹에 VM `192.168.0.211:8081` 아웃바운드 규칙이 들어갔고(지금 아웃바운드는 443, 3306, 8081), 환경변수 `APP_INFO_API_BASE_URL` · `APP_INFO_API_SECRET`이 서버의 `app.env`에 들어갔다. 비밀은 Parameter Store `/corebank/info-api-secret`에서 서버가 켜질 때 읽는다. WAS 2대에서 연결이 `401`(인증 전)이고, 헤더 `X-Info-Api-Secret`을 담아 `/info/product-subscriptions`를 호출하면 `200`이었다. **관리자 화면 요청이 WAS를 거쳐 정보계 API의 `asOf` 응답을 받는 것은 아직 확인하지 못했다**(로그인이 필요해 UAT 또는 #818에서 확인한다).
- WAS → VM의 모의 대외기관: **미완(#761).** 선행 조건: 지금 WAS 보안 그룹의 아웃바운드에 모의 대외기관 포트(TCP 9300)는 없다. 연동 전에 목적지 IP와 서비스 포트를 확인하고 `modules/network/security_groups.tf`에 egress 규칙을 추가한다. 포트가 확정되기 전에는 규칙을 열지 않는다.

### 4-4. 정상 운영 전 조건

- §3-7을 적용하고 VM을 재부팅해 터널과 경로가 자동 복구되는지 확인한 뒤에 정상 운영으로 넘긴다. 확인 항목: `sudo ipsec statusall`의 `ESTABLISHED`·`INSTALLED, TUNNEL`, `ip route get <WAS 사설 IP>`가 `dev vti1`, RDS 3306 `nc` 성공, 컨테이너 `Up`, `systemctl is-active strongswan-starter vpn-vti`가 `active`.
- **완료(2026-10-29):** 재부팅 후 약 1분 안에 위 항목이 모두 정상이었다.
- 위 확인은 VM 재부팅 시나리오만 검증한 것이다. **AWS가 세션 삭제(DELETE)를 보낸 경우에는 자동으로 복구되지 않는다**(2026-11-01, #851).

## 5. 문제가 생기면

| 증상 | 원인 | 대응 |
|---|---|---|
| 설정 후 SSH가 끊김 | `mark`가 없거나 `install_routes`가 켜져 모든 트래픽이 터널로 감 | §2의 30분 자동 정지를 기다리거나 콘솔로 접속해 `sudo ipsec stop`. AWS 쪽 VPN 연결을 삭제하면 VM의 DPD가 정책을 푼다 |
| `unknown keyword` | strongSwan 5.9.5 `ipsec.conf`가 모르는 키워드 | `mark=100` + VTI를 쓴다 |
| 터널은 UP인데 통신 안 됨 | VTI나 `10.30.0.0/16` 경로가 없음 | §3-5를 확인한다 |
| 큰 응답이 끊김 | MTU 문제 | `vti1`의 mtu 1400 확인 (시험에서 MSS 1360이 맞았다) |
| 로그에 `received DELETE for IKE_SA` 뒤로 `establishing`·`retransmit`이 없고 `Security Associations (0 up, 0 connecting)` | AWS가 터널을 정상 종료(장비 교체 등)했는데 VM이 재연결하지 않음 | `sudo ipsec restart`로 다시 붙인다. 그래도 `connecting`에서 안 올라오면 아래 행 |
| `Security Associations (0 up, 1 connecting)`이고 로그에 `retransmit`만 반복, 인증·제안 오류 없음 | AWS가 연결 요청에 응답하지 않음. 교육장 공인 IP가 바뀌어 고객 게이트웨이 IP와 다른 경우가 있었다 | §5-1 |

### 5-1. 교육장 공인 IP가 바뀌었을 때 (2026-11-03, #851)

터널이 끊기고 재시작해도 `connecting`에서 안 올라오면 의심한다. 주소는 화면에 찍지 않고 같음/다름과 해시 앞 4글자만 본다.

1. 비교: 현재 공인 IP와 고객 게이트웨이에 등록된 IP가 같은지 본다. 조회가 하나라도 실패하거나 IPv4 형식이 아니면 `조회실패`가 나오고, 이때는 같음/다름으로 판단하지 않는다.
```bash
v(){ printf '%s' "$1" | grep -Eq '^[0-9]{1,3}(\.[0-9]{1,3}){3}$'; }; CUR=$(curl -fsS -m 5 https://checkip.amazonaws.com | tr -d '\n'); REG=$(aws ec2 describe-customer-gateways --customer-gateway-ids <CGW ID> --query 'CustomerGateways[0].IpAddress' --output text | tr -d '\n'); if v "$CUR" && v "$REG"; then [ "$CUR" = "$REG" ] && echo 같음 || echo 다름; else echo 조회실패; fi; unset CUR REG
```
- 교육장 안의 PC(VM과 같은 공인 IP)에서 실행한다. `다름`이면 아래로 진행하고, `조회실패`면 네트워크, AWS 프로필, `<CGW ID>`를 먼저 확인한다.
2. 새 고객 게이트웨이를 만든다(기존 것은 정상 연결이 확인될 때까지 지우지 않는다).
```bash
aws ec2 create-customer-gateway --type ipsec.1 --bgp-asn 65000 --ip-address "$(curl -fsS -m 5 https://checkip.amazonaws.com | tr -d '\n')" --query 'CustomerGateway.CustomerGatewayId' --output text
```
3. 변경 전에 AWS 터널의 외부 주소를 기록한다. 터널 번호는 배열 순서로 정하지 않는다(`VgwTelemetry`에 터널 번호 필드가 없다). 두 터널의 해시를 구하고, VM이 실제로 쓰는 터널(`ipsec.conf`의 `right`)의 해시와 같은 쪽을 "터널 1"로 본다.
```bash
aws ec2 describe-vpn-connections --vpn-connection-ids <VPN ID> --query 'VpnConnections[0].VgwTelemetry[*].OutsideIpAddress' --output text | tr '\t' '\n' | while read -r ip; do printf '%s' "$ip" | shasum -a 256 | cut -c1-4; done
```
VM에서:
```bash
sudo awk -F= '/^[[:space:]]*right=/{print $2; exit}' /etc/ipsec.conf | tr -d '\n' | sha256sum | cut -c1-4
```
terraform 출력값과도 대조한다(주소는 출력하지 않는다). 이 값은 VM `right`나 AWS 해시와 같아야 한다.
```bash
cd terraform/main && terraform output -raw vpn_tunnel1_address | tr -d '\n' | shasum -a 256 | cut -c1-4
```
- 세 값(AWS 두 터널 중 하나, VM `right`, terraform 출력)이 같은 해시로 모이지 않으면 **여기서 멈추고** 어느 쪽이 낡았는지 확인한다. 순서만 믿고 4단계로 넘어가지 않는다. 2026-11-03에는 셋이 같았다.
4. 기존 VPN 연결을 새 고객 게이트웨이로 바꾼다.
```bash
aws ec2 modify-vpn-connection --vpn-connection-id <VPN ID> --customer-gateway-id <새 CGW ID>
```
- 상태가 `modifying`인 동안 **약 20분** 걸렸다(2026-11-03, 터널을 하나씩 교체했고 터널 2는 약 10분, 터널 1은 약 20분 시점에 올라왔다). 오류가 아니므로 이 동안 다른 변경을 겹치지 않는다. 터널마다 "Customer Initiated" 교체 알림 메일이 온다.
5. `available`이 되면 3단계의 AWS 명령을 다시 실행해 해시를 비교한다.
- VM `right`와 같은 해시가 그대로 있으면 `right`는 바꾸지 않는다(2026-11-03에는 같았고 VM 설정을 바꾸지 않았는데 붙었다).
- 달라졌으면 VM의 `/etc/ipsec.conf`의 `right`와 `/etc/ipsec.secrets`의 두 번째 식별자를 새 값으로 고친다. 그리고 `vti1`이 낡은 주소로 남지 않게 아래 순서로 한다(§3-7).
```bash
sudo ipsec restart
```
```bash
sudo ip tunnel del vti1
```
```bash
sudo systemctl restart vpn-vti
```
- 로컬 식별자도 따로 확인한다. VM 공인 IP가 바뀌었으니 `ipsec.conf`의 `leftid`와 `ipsec.secrets`의 첫 번째 식별자가 옛 IP로 남아 있을 수 있다. 아래 세 값의 해시를 비교한다(현재 공인 IP, `leftid`, `ipsec.secrets` 첫 식별자).
```bash
curl -fsS -m 5 https://checkip.amazonaws.com | tr -d '\n' | sha256sum | cut -c1-4
```
```bash
sudo awk -F= '/^[[:space:]]*leftid=/{print $2; exit}' /etc/ipsec.conf | tr -d '\n' | sha256sum | cut -c1-4
```
```bash
sudo awk 'NF && $1 !~ /^#/{print $1; exit}' /etc/ipsec.secrets | tr -d '\n' | sha256sum | cut -c1-4
```
- 식별자가 현재 공인 IP와 다를 때는 터널 상태로 나눈다.
  - **터널이 정상 연결된 경우:** 불일치만 기록하고 바꾸지 않는다. 정상인 VPN을 불필요하게 재시작하지 않는다. 변경은 P2와 정한 유지보수 시점에 `closeaction` 변경 등과 묶어 한 번의 재시작으로 한다. 2026-11-03이 이 경우였다(`leftid`와 `ipsec.secrets` 첫 식별자가 둘 다 옛 공인 IP 그대로였는데 터널이 붙었다. AWS가 이 불일치를 거부하지 않았다).
  - **인증 실패 등으로 변경이 필요한 경우:** `leftid`와 `ipsec.secrets` 첫 식별자를 같은 값(새 공인 IP)으로 함께 고친다(한쪽만 바꾸면 PSK를 못 찾아 인증이 실패할 수 있다, 추정). 그다음 `sudo ipsec restart`로 다시 연결하고 `Security Associations`가 `1 up`인지 확인한다.
6. 확인: AWS 쪽 터널 1이 UP, VM에서 `Security Associations (1 up, 0 connecting)`, `ip route get <WAS 사설 IP>`가 `dev vti1`, RDS 3306이 열려 있다. `vti1`의 remote 주소가 `right`와 같은지도 본다(해시가 같아야 한다).
```bash
ip -d tunnel show vti1 | grep -oE 'remote [0-9.]+' | awk '{print $2}' | tr -d '\n' | sha256sum | cut -c1-4
```
7. 복제를 확인한다. 레플리카는 재시도 10회(간격 60초)를 쓰고 나면 IO 스레드가 멈춰 터널이 복구돼도 **자동으로 재개되지 않는다.** GTID를 RDS의 `gtid_purged`와 비교한 뒤 재개한다(P2).
8. terraform 코드와 state를 맞춘다(#868). 이 VPN은 terraform이 관리하는데 3·4단계는 AWS에서 직접 바꾼 것이라, 그대로 두면 다음 `plan`이 고객 게이트웨이를 교체하려 든다(2026-11-03 `plan`에서 확인: 1 add, 1 destroy와 VPN 연결 수정). 정리 전에는 `terraform plan`·`apply`를 하지 않고, 공유 state라 팀에 먼저 알린다. S3의 state 파일은 직접 고치지 않는다.
- 새 고객 게이트웨이의 `Name` 태그를 코드(`<name_prefix>-cgw-onprem`)와 같게 맞춘다. 다르면 가져온 뒤 `plan`에 태그 수정이 남는다.
- 장부에서 옛 고객 게이트웨이를 빼고 새 것을 가져온다. VPN 연결은 가져오지 않는다. VPN ID가 그대로라 `aws_vpn_connection_route`도 영향이 없고, refresh로 새 고객 게이트웨이를 가리키는지 확인한다.
```bash
terraform state rm module.vpn.aws_customer_gateway.onprem
```
```bash
terraform import module.vpn.aws_customer_gateway.onprem <새 CGW ID>
```
- `modules/vpn/variables.tf`의 `customer_gateway_ip` 기본값을 새 공인 IP로 고치는 PR을 올린다. 머지는 아래 `plan` 확인 뒤에 한다.
- `plan`(`-lock=false`, 저장 안 함)에서 `module.vpn` 항목이 하나도 없고 예상하지 않은 destroy/recreate가 없어야 한다. 그 뒤에만 PR을 머지한다.
- 이 절차는 #868에서 처음 수행한다. 수행 결과를 이 절에 다시 반영한다. `terraform/README.md`의 "VPN을 포함해 apply하기 전에 공인 IP 확인" 절차도 함께 본다.

## 6. 정리 (환경이 끝날 때)

- AWS: `terraform destroy`에 VPN 연결·VGW·CGW가 포함된다(PH-55). VPN을 먼저 지우면 VM의 DPD가 정책을 풀어 SSH가 유지된다.
- VM: 먼저 `sudo systemctl disable --now vpn-vti.service`로 서비스를 끄고 `/etc/systemd/system/vpn-vti.service`와 `/usr/local/sbin/vpn-vti-up.sh`를 지운 뒤 `sudo systemctl daemon-reload`를 한다. 이어서 `ipsec stop`, strongSwan 제거, `/etc/ipsec.*`와 `/etc/strongswan.d/zz-vpn.conf`, `vti1`과 경로를 삭제한다.
- PSK가 남은 곳(클립보드, Terraform 상태 파일의 S3, 임시 파일)을 정리한다.
