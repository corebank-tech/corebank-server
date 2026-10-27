# PH-100-② VPN — VM(온프레미스) 쪽 설정 런북

> 새 계정 VPC(10.30.0.0/16)와 부트캠프 VM(192.168.0.0/24)을 Site-to-Site VPN으로 잇는 중 **VM 쪽** 절차를 적는다(#753). AWS 쪽은 `terraform/main/modules/vpn`이다.
> 값은 `<...>` 자리표시자로 둔다. **공유 비밀키(PSK)와 터널 주소는 이 문서·저장소·채팅에 적지 않는다.**
> 10/19에 일회용 AWS 환경(임시 VPC 10.99.0.0/16)과 테스트 VM으로 같은 구조를 시험해 터널이 서고 양방향 통신이 된 것을 확인했다. 아래 설정은 그 시험에서 나온 것이다.

## 0. 전제와 아직 확인되지 않은 것

- 방식: **VM이 먼저 터널을 여는 아웃바운드 방식**이다. VM은 NAT 뒤(192.168.0.211)라서 공유기 포트포워딩은 필요 없다(10/19 시험).
- 터널은 **터널 1만** 설정한다. AWS가 터널을 2개 만들지만 터널 2는 DOWN이어도 정상이다.
- Customer Gateway에 등록된 공인 IP는 `58.230.206.76`이다(교육장 NAT 출구, VM에서 `curl -s ifconfig.me`).
- **최종 VM에서 아직 확인하지 못한 것**: UDP 500/4500 통과, 같은 방식으로 터널이 서는지, 장시간 유지·rekey, 터널 2 장애 전환, 실제 포트(RDS 3306·WAS 8080·정보계 API).
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
2. `strongswan-starter`의 부팅 자동 시작을 끈다(재부팅 시 잠김 방지).
   ```bash
   sudo systemctl disable strongswan-starter
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
- `leftid`는 내부 IP(192.168.0.211)가 아니라 **공인 IP**여야 한다.
- `mark=100`이 빠지면 모든 트래픽이 터널로 들어간다.
- `if_id_in` 같은 XFRM 키워드는 strongSwan 5.9.5의 `ipsec.conf`가 모른다(`unknown keyword`). 쓰지 않는다.

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
- 재부팅하면 `vti1`과 경로가 사라진다. 지속시키는 방법은 터널이 안정된 뒤 정한다.

### 3-6. 시작
```bash
sudo ipsec restart
sudo ipsec statusall
```

## 4. 확인

### 4-1. 터널 기본 검증 (§2의 타이머가 걸린 채로, 30분 안에)

1. 터널 상태: `sudo ipsec statusall`에 `ESTABLISHED`, `INSTALLED, TUNNEL`이 보인다. AWS 콘솔(VPC > Site-to-Site VPN 연결)에서 터널 1이 UP이고 터널 2는 DOWN이어도 정상이다.
2. VM 기본 경로가 그대로인지: `ip route`에서 `default via ...`가 터널이 아닌 원래 경로다. SSH가 유지되는지 같이 본다.
3. VM → AWS: 먼저 `command -v nc`로 `nc`가 있는지 확인하고, 없으면 `sudo apt install netcat-openbsd`로 설치한다. 그다음 `nc -vz <WAS 사설 IP> 8080` (TCP 연결 검사, 메트릭 수집 경로, 이 PR의 WAS 보안 그룹 규칙). WAS 보안 그룹은 ICMP를 허용하지 않고 RDS도 ping에 응답하지 않아서 `ping`은 터널이 정상이어도 실패한다.
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
- VM → RDS 3306: 복제 계정은 P2 PH-70-②에서 정한다. 보안 그룹의 RDS 규칙은 P2가 추가한 뒤 `nc -vz <RDS 사설 IP> 3306`으로 확인한다.
- WAS → VM의 정보계 API·모의 대외기관 (VM이 받는 포트가 정해진 뒤). **선행 조건:** 지금 WAS 보안 그룹의 아웃바운드는 443과 VPC 내부 3306뿐이다. 연동 전에 목적지 IP와 서비스 포트(모의 대외기관은 TCP 9300)를 확인하고 `modules/network/security_groups.tf`에 egress 규칙을 추가한다. 포트가 확정되기 전에는 규칙을 열지 않는다.

### 4-4. 정상 운영 전 조건

- 재부팅하면 `vti1`과 `10.30.0.0/16` 경로가 사라지고, `strongswan-starter`는 꺼 둔 상태라 터널이 자동으로 다시 서지 않는다. 터널이 안정된 뒤 서비스 시작과 VTI·경로를 영속화하고, VM을 재부팅해 터널과 경로가 자동 복구되는지 확인하기 전에는 정상 운영으로 넘기지 않는다.

## 5. 문제가 생기면

| 증상 | 원인 | 대응 |
|---|---|---|
| 설정 후 SSH가 끊김 | `mark`가 없거나 `install_routes`가 켜져 모든 트래픽이 터널로 감 | §2의 30분 자동 정지를 기다리거나 콘솔로 접속해 `sudo ipsec stop`. AWS 쪽 VPN 연결을 삭제하면 VM의 DPD가 정책을 푼다 |
| `unknown keyword` | strongSwan 5.9.5 `ipsec.conf`가 모르는 키워드 | `mark=100` + VTI를 쓴다 |
| 터널은 UP인데 통신 안 됨 | VTI나 `10.30.0.0/16` 경로가 없음 | §3-5를 확인한다 |
| 큰 응답이 끊김 | MTU 문제 | `vti1`의 mtu 1400 확인 (시험에서 MSS 1360이 맞았다) |

## 6. 정리 (환경이 끝날 때)

- AWS: `terraform destroy`에 VPN 연결·VGW·CGW가 포함된다(PH-55). VPN을 먼저 지우면 VM의 DPD가 정책을 풀어 SSH가 유지된다.
- VM: `ipsec stop`, strongSwan 제거, `/etc/ipsec.*`와 `/etc/strongswan.d/zz-vpn.conf`, `vti1`과 경로 삭제
- PSK가 남은 곳(클립보드, Terraform 상태 파일의 S3, 임시 파일)을 정리한다.
