"""CoreBank 인프라 — 상세 구성. 핵심 흐름(core.py)과 같은 배치·글자 크기에 네트워크·보안·배포를 더한다.
근거: docs/phase2 README §3-4·§3-5·§3-8, tasks PH-42·47·50·51·54·55·58·70·72·76·77·82, .github/workflows/corebank.yml.
2026-09-23 리드 결정: Redis(ElastiCache) Multi-AZ · AZ별 NAT · 정보계·모의 대외기관·모니터링 온프레미스(VM 2대)
· RDS→온프레미스 binlog 복제 · AWS↔온프레미스 Site-to-Site VPN · 정보계 ETL COB 뒤 일 1회 · RDS 자동 백업
· SSM 하이브리드 배포 · 내비게이션 API는 WAS에 함께 배치 · WAS ASG min 2(AZ별 1대)."""
import os
import sys

from render import HERE, render

ASSETS = os.path.join(HERE, "..", "..", "docs", "assets")

W, H = 2520, 1720
FS = 1.7   # 글자 배율 — core.py와 같다
ISIZE = 60

BANDS = [
    (400, 210, "Presentation Tier", "Cloudflare Pages · React", "#eef6ff"),
    (625, 640, "Application Tier", "ALB → WAS · 정보계 API\n대외기관 · 모니터링", "#effaf2"),
    (1275, 430, "Data Tier", "Redis · RDS\n레플리카 · 마트", "#fff6ec"),
]

# (x, y, w, h, 라벨, 색, 점선, 아이콘, 라벨 위치)
GROUPS = [
    (30, 770, 260, 400, "배포 (CI/CD)", "ops", True, None),
    (310, 635, 1250, 1055, "AWS · ap-northeast-2 (서울)", "aws", False, "AWSCloudlogo"),
    (330, 670, 240, 1000, "리전 관리형 서비스", "neutral", True, None),
    (590, 690, 950, 980, "VPC 10.30.0.0/16", "vpc", False, "VirtualprivatecloudVPC"),
    (610, 800, 490, 840, "가용 영역 2a", "az", True, None),
    (1130, 800, 330, 840, "가용 영역 2c", "az", True, None),
    (625, 840, 460, 150, "public-2a", "subnet_pub", False, None),
    (1145, 840, 300, 150, "public-2c", "subnet_pub", False, None, "bl"),  # 좌상단은 ALB 배지와 겹친다
    (625, 1010, 460, 250, "app-2a", "subnet_priv", False, None),
    (1145, 1010, 300, 250, "app-2c", "subnet_priv", False, None),
    (625, 1280, 460, 345, "data-2a", "subnet_priv", False, None),
    (1145, 1280, 300, 345, "data-2c", "subnet_priv", False, None),
    (640, 1050, 810, 195, "Auto Scaling group · min 2 (AZ별 1대)", "asg", True, "AutoScalinggroup", "bl"),
    (1710, 635, 785, 1055, "온프레미스 · 신한DS 자체 서버", "vm", True, "Corporatedatacenter"),
    (1870, 970, 200, 445, "대외·모니터링 VM", "neutral", False, None),
    (2095, 665, 380, 1005, "정보계 VM", "neutral", False, None),
]

# (cx, cy, 아이콘, 이름, 부제, 배지)
NODES = [
    (1115, 140, "Users", "사용자", "브라우저 · 모바일"),
    (1115, 300, "@dns", "DNS", "가비아"),
    (740, 470, "si-cloudflare", "Cloudflare Pages", "React FE · 고객·관리자", "si-react"),
    # 배포
    (160, 850, "si-githubactions", "GitHub Actions", "빌드 · 배포"),
    (160, 1040, "si-docker", "Docker Hub", "이미지 · NAT로 pull"),
    # 리전 관리형 서비스
    (450, 755, "AWSWAF", "AWS WAF", "ALB 웹 ACL · 레이트리밋"),
    (450, 905, "AWSCertificateManager", "ACM", "ALB 인증서"),
    (450, 1055, "AWSSecretsManager", "Secrets Manager", "앱 비밀값"),
    (450, 1205, "AWSSystemsManager", "SSM", "배포 · 접속 (하이브리드)"),
    (450, 1355, "AWSCloudTrail", "CloudTrail", "API 감사 기록"),
    (450, 1505, "AmazonSimpleStorageService", "S3 Object Lock", "감사로그 WORM\nGOVERNANCE 1일"),
    # 진입 · 네트워크
    (1115, 760, "AmazonVPCInternetGateway", "", ""),
    (1115, 890, "ElasticLoadBalancing", "ALB", "", "AWSWAF"),  # 부제는 서브넷 경계와 겹쳐 뺀다(HTTPS는 진입선 라벨, WAF는 배지)
    (800, 900, "AmazonVPCNATGateway", "", ""),
    (1380, 900, "AmazonVPCNATGateway", "", ""),
    # 애플리케이션
    (830, 1110, "AmazonEC2", "WAS", "Spring Boot · 내비게이션 API", "si-spring"),
    (1290, 1110, "AmazonEC2", "WAS", "Spring Boot · 내비게이션 API", "si-spring"),
    # 데이터
    (910, 1350, "AmazonElastiCache", "Redis", "ElastiCache · Primary"),
    (1290, 1350, "AmazonElastiCache", "Redis", "ElastiCache · Replica (대기)"),
    (740, 1520, "AmazonRDS", "RDS MySQL", "Primary · 자동 백업"),
    (1330, 1520, "AmazonRDS", "RDS MySQL", "Standby · 대기 전용"),
    # VPN
    (1500, 1280, "AmazonVPCVPNGateway", "", ""),
    (1790, 1280, "AmazonVPCCustomerGateway", "", ""),
    # 온프레미스
    (2285, 800, "si-nginx", "Nginx", "HTTPS 종료"),
    (1970, 1110, "@ext", "모의 대외기관", "타행 · 고정길이 전문"),
    (2190, 1110, "si-spring", "계정계 API", "대기\n전환 시 기동"),
    (2380, 1110, "si-spring", "정보계 API", "Spring Boot\n상시"),
    (1970, 1280, "si-prometheus", "모니터링", "Prometheus\nGrafana", "si-grafana"),
    (2190, 1520, "si-mysql", "MySQL 레플리카", "binlog 복제"),
    (2380, 1520, "si-mysql", "정보계 마트", "COB 후 적재\nPII 없음"),
]

TUBES = [(1534, 1756, 1280, "Site-to-Site VPN\nIPsec")]

# (경로, 색, 라벨, 라벨좌표, 정렬, 화살표, 점선) — 경로가 None이면 라벨만
EDGES = [
    # 이름표(아이콘 옆)
    (None, "neutral", "IGW", (1152, 740), "start", False, False),
    (None, "neutral", "NAT", (835, 906), "start", False, False),
    (None, "neutral", "NAT", (1345, 906), "end", False, False),
    # 진입
    ("M1115 240 V 268", "flow", None, None, "middle", True, False),
    ("M1085 300 H 740 V 438", "flow", "화면 파일", (910, 292), "middle", True, False),
    ("M1115 402 V 728", "flow", "API · HTTPS", (1125, 590), "start", True, False),
    ("M1115 792 V 858", "flow", None, None, "middle", True, False),
    ("M1145 300 H 2285 V 768", "neutral", "AWS 사용 불가 시 계정계 API", (1600, 292), "middle", True, True),
    # ALB → WAS
    ("M1115 990 V 1025 H 830 V 1078", "flow", None, None, "middle", True, False),
    ("M1115 1025 H 1290 V 1078", "flow", None, None, "middle", True, False),
    # 아웃바운드 (AZ별 NAT → IGW)
    ("M800 1050 V 932", "neutral", "egress", (808, 1000), "start", True, False),
    ("M1380 1050 V 932", "neutral", "egress", (1388, 1000), "start", True, False),
    ("M800 868 V 760 H 1083", "neutral", None, None, "middle", True, False),
    ("M1380 868 V 760 H 1147", "neutral", None, None, "middle", True, False),
    # 배포
    ("M160 950 V 1008", "ops", "push", (170, 985), "start", True, False),
    ("M190 850 H 300 V 1205 H 418", "ops", "OIDC", (245, 842), "middle", True, False),
    ("M482 1205 H 638", "ops", "배포", (590, 1197), "middle", True, False),
    # WAS(ASG) → 데이터
    ("M910 1245 V 1318", "flow", "6379 · Primary endpoint", (918, 1280), "start", True, False),
    ("M740 1245 V 1488", "flow", "3306 · Primary endpoint", (748, 1478), "start", True, False),
    ("M942 1350 H 1258", "data", "Async replication", (1100, 1340), "middle", True, False),
    (None, "data", "Auto failover", (1100, 1378), "middle", False, False),
    ("M772 1520 H 1298", "data", "Sync replication", (1035, 1510), "middle", True, False),
    (None, "data", "Auto failover", (1035, 1548), "middle", False, False),
    # AWS ↔ VPN Gateway
    ("M1450 1060 H 1512 V 1248", "flow", "정보계 조회", (1481, 1048), "middle", True, False),
    ("M1450 1225 H 1488 V 1248", "neutral", "대외 전문", (1442, 1232), "end", True, False),
    ("M1468 1280 H 1430 V 1247", "neutral", "scrape", (1422, 1290), "end", True, True),
    ("M740 1618 V 1655 H 1500 V 1312", "data", "binlog", (1100, 1661), "middle", True, False),
    # Customer Gateway ↔ 온프레미스
    ("M1775 1250 V 1110 H 1938", "neutral", None, None, "middle", True, False),
    ("M1938 1280 H 1822", "neutral", None, None, "middle", True, True),
    ("M1805 1250 V 945 H 2380 V 1078", "flow", None, None, "middle", True, False),
    ("M1790 1312 V 1520 H 2158", "data", None, None, "middle", True, False),
    # 온프레미스 안
    ("M2285 900 V 915 H 2190 V 1078", "neutral", None, None, "middle", True, True),
    ("M2380 1240 V 1488", "flow", "조회", (2390, 1400), "start", True, False),
    ("M2190 1240 V 1488", "neutral", "전환 시 승격", (2200, 1400), "start", True, True),
    ("M2222 1520 H 2348", "data", "ETL", (2285, 1510), "middle", True, False),
]

LEGEND = (W - 30 - 360 * FS, 30, [
    ("flow", False, "Request (HTTP/TCP)"),
    ("data", False, "Replication · ETL"),
    ("neutral", False, "External TCP (전문) · Egress"),
    ("neutral", True, "Metrics scrape · Failover path"),
    ("ops", False, "Deploy (CI/CD)"),
])

if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ASSETS, "infra-detail.svg")
    render(out, w=W, h=H, title="CoreBank 인프라 — 상세 구성",
           aria="핵심 흐름에 서브넷(public·app·data), AZ별 NAT, IGW, 리전 관리형 서비스(WAF·ACM·SSM·Secrets Manager·CloudTrail·S3 Object Lock), "
                "GitHub Actions·Docker Hub·SSM 배포 경로를 더한 상세 구성도. Redis(ElastiCache)와 RDS는 2a에 Primary, 2c에 Replica·Standby를 둔 Multi-AZ이고, "
                "AWS와 온프레미스는 Site-to-Site VPN으로 연결된다.",
           heading=("CoreBank 인프라 — 상세 구성",),
           bands=BANDS, groups=GROUPS, nodes=NODES, edges=EDGES, tubes=TUBES, legend=LEGEND,
           fs=FS, isize=ISIZE)
    print(out)
