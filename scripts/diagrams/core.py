"""CoreBank 인프라 — 핵심 흐름(하이브리드 3-Tier).
근거: docs/phase2 README §3-5·§3-8, tasks PH-47·50·51·54·58·70·76·77.
2026-09-23 리드 결정: Redis(ElastiCache) Multi-AZ · 정보계·모의 대외기관·모니터링 온프레미스(VM 2대)
· RDS→온프레미스 binlog 복제 · AWS↔온프레미스 Site-to-Site VPN · 정보계 ETL COB 뒤 일 1회 · 내비게이션 API는 WAS에 함께 배치
· WAS ASG min 2(AZ별 1대)."""
import os
import sys

from render import HERE, render

ASSETS = os.path.join(HERE, "..", "..", "docs", "assets")

W, H = 2220, 1720
FS = 1.7   # 글자 배율
ISIZE = 60  # 아이콘 크기

BANDS = [
    (400, 210, "Presentation Tier", "Cloudflare Pages · React", "#eef6ff"),
    (625, 650, "Application Tier", "ALB → WAS · 정보계 API\n대외기관 · 모니터링", "#effaf2"),
    (1290, 410, "Data Tier", "Redis · RDS\n레플리카 · 마트", "#fff6ec"),
]

# (x, y, w, h, 라벨, 색, 점선, 아이콘, 라벨 위치)
GROUPS = [
    (300, 635, 960, 1050, "AWS · ap-northeast-2 (서울)", "aws", False, "AWSCloudlogo"),
    (320, 675, 920, 993, "VPC 10.30.0.0/16", "vpc", False, "VirtualprivatecloudVPC"),
    (340, 860, 490, 780, "가용 영역 2a", "az", True, None),
    (860, 860, 300, 780, "가용 영역 2c", "az", True, None),
    (400, 895, 750, 215, "Auto Scaling group · min 2 (AZ별 1대)", "asg", True, "AutoScalinggroup", "bl"),
    (1410, 635, 785, 1050, "온프레미스 · 신한DS 자체 서버", "vm", True, "Corporatedatacenter"),
    (1570, 875, 200, 405, "대외·모니터링 VM", "neutral", False, None),
    (1795, 665, 380, 1000, "정보계 VM", "neutral", False, None),
]

# (cx, cy, 아이콘, 이름, 부제, 배지)
NODES = [
    (845, 140, "Users", "사용자", "브라우저 · 모바일"),
    (845, 290, "@dns", "DNS", "가비아"),
    (470, 470, "si-cloudflare", "Cloudflare Pages", "React FE · 고객·관리자", "si-react"),
    (845, 735, "ElasticLoadBalancing", "ALB", "HTTPS · WAF", "AWSWAF"),
    (560, 950, "AmazonEC2", "WAS", "Spring Boot · 내비게이션 API", "si-spring"),
    (1005, 950, "AmazonEC2", "WAS", "Spring Boot · 내비게이션 API", "si-spring"),
    (640, 1370, "AmazonElastiCache", "Redis", "ElastiCache · Primary"),
    (1010, 1370, "AmazonElastiCache", "Redis", "ElastiCache · Replica (대기)"),
    (450, 1530, "AmazonRDS", "RDS MySQL", "Primary · 자동 백업"),
    (1040, 1530, "AmazonRDS", "RDS MySQL", "Standby · 대기 전용"),
    (1200, 1150, "AmazonVPCVPNGateway", "", ""),
    (1490, 1150, "AmazonVPCCustomerGateway", "", ""),
    (1985, 735, "si-nginx", "Nginx", "HTTPS 종료"),
    (1670, 950, "@ext", "모의 대외기관", "타행 · 고정길이 전문"),
    (1890, 950, "si-spring", "계정계 API", "대기\n전환 시 기동"),
    (2080, 950, "si-spring", "정보계 API", "Spring Boot\n상시"),
    (1670, 1150, "si-prometheus", "모니터링", "Prometheus\nGrafana", "si-grafana"),
    (1890, 1530, "si-mysql", "MySQL 레플리카", "binlog 복제"),
    (2080, 1530, "si-mysql", "정보계 마트", "COB 후 적재\nPII 없음"),
]

TUBES = [(1234, 1456, 1150, "Site-to-Site VPN\nIPsec")]

# (경로, 색, 라벨, 라벨좌표, 정렬, 화살표, 점선) — 경로가 None이면 라벨만
EDGES = [
    # 진입
    ("M845 240 V 258", "flow", None, None, "middle", True, False),
    ("M815 290 H 470 V 438", "flow", "화면 파일", (640, 282), "middle", True, False),
    ("M845 392 V 703", "flow", "API · HTTPS", (855, 590), "start", True, False),
    ("M875 290 H 1985 V 703", "neutral", "AWS 사용 불가 시 계정계 API", (1430, 282), "middle", True, True),
    # ALB → WAS
    ("M845 836 V 870 H 560 V 918", "flow", None, None, "middle", True, False),
    ("M845 870 H 1005 V 918", "flow", None, None, "middle", True, False),
    # WAS(ASG) → 데이터
    ("M640 1110 V 1338", "flow", "6379 · Primary endpoint", (648, 1230), "start", True, False),
    ("M450 1110 V 1498", "flow", "3306 · Primary endpoint", (442, 1300), "end", True, False),
    ("M672 1370 H 978", "data", "Async replication", (825, 1360), "middle", True, False),
    (None, "data", "Auto failover", (825, 1398), "middle", False, False),
    ("M482 1530 H 1008", "data", "Sync replication", (745, 1520), "middle", True, False),
    (None, "data", "Auto failover", (745, 1558), "middle", False, False),
    # AWS ↔ VPN Gateway
    ("M1150 960 H 1212 V 1118", "flow", "정보계 조회", (1181, 948), "middle", True, False),
    ("M1150 1075 H 1188 V 1118", "neutral", "대외 전문", (1142, 1082), "end", True, False),
    ("M1168 1150 H 1120 V 1112", "neutral", "scrape", (1112, 1140), "end", True, True),
    ("M450 1628 V 1655 H 1200 V 1182", "data", "binlog", (820, 1661), "middle", True, False),
    # Customer Gateway ↔ 온프레미스
    ("M1475 1120 V 950 H 1638", "neutral", None, None, "middle", True, False),
    ("M1638 1150 H 1522", "neutral", None, None, "middle", True, True),
    ("M1505 1120 V 862 H 2080 V 918", "flow", None, None, "middle", True, False),
    ("M1490 1182 V 1530 H 1858", "data", None, None, "middle", True, False),
    # 온프레미스 안
    ("M1985 836 V 848 H 1890 V 918", "neutral", None, None, "middle", True, True),
    ("M2080 1080 V 1498", "flow", "조회", (2090, 1300), "start", True, False),
    ("M1890 1080 V 1498", "neutral", "전환 시 승격", (1900, 1300), "start", True, True),
    ("M1922 1530 H 2048", "data", "ETL", (1985, 1520), "middle", True, False),
]

LEGEND = (W - 30 - 360 * FS, 30, [
    ("flow", False, "Request (HTTP/TCP)"),
    ("data", False, "Replication · ETL"),
    ("neutral", False, "External TCP (전문)"),
    ("neutral", True, "Metrics scrape · Failover path"),
])

if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ASSETS, "infra-core.svg")
    render(out, w=W, h=H, title="CoreBank 인프라 — 핵심 흐름",
           aria="사용자가 DNS를 거쳐 Cloudflare Pages에서 화면을 받고, API는 ALB를 거쳐 두 가용 영역의 WAS로 간다. WAS는 Redis(ElastiCache)와 RDS를 쓰고, "
                "둘 다 2a에 Primary, 2c에 Replica·Standby를 둔 Multi-AZ다. AWS와 온프레미스는 Site-to-Site VPN으로 연결되고, 그 터널로 RDS binlog 복제, "
                "모의 대외기관 전문, 온프레미스 모니터링의 WAS 수집, WAS가 인증한 뒤 부르는 정보계 조회가 지난다. 온프레미스 Nginx는 AWS를 쓸 수 없을 때만 계정계 API 진입점이 된다. 온프레미스는 VM 두 대로 정보계와 대외기관·모니터링을 운영한다.",
           heading=("CoreBank 인프라 — 핵심 흐름",),
           bands=BANDS, groups=GROUPS, nodes=NODES, edges=EDGES, tubes=TUBES, legend=LEGEND,
           fs=FS, isize=ISIZE)
    print(out)
