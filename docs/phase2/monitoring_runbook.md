# 모니터링 런북 (Prometheus · Alertmanager · Grafana, #619)

> 11/18 시연 VM(`192.168.0.211`)에서 계정계 API의 지표를 보고 알람을 받는 구성이다. 설정 파일은 [`docker/monitoring/`](../../docker/monitoring/)에 있고, 이 문서는 설치, 알람별 대응, 알람에서 인지까지의 시간 측정을 다룬다.
> 3-Tier는 11/16에 철거되어 수집 대상은 **같은 VM의 계정계 API 하나**다(처음 계획한 AWS WAS 수집은 없어졌다).
> 비밀값(Grafana 관리자 비밀번호, Discord 웹훅 주소)은 저장소에 두지 않고 VM 파일로만 둔다.

## 1. 구성

| 구성 요소 | 이미지 | 주소 | 하는 일 |
|---|---|---|---|
| Prometheus | `prom/prometheus:v3.15.1` | `127.0.0.1:9090`(VM 안에서만) | 15초마다 계정계 API 지표를 수집하고 알람 규칙을 평가한다(보관 7일) |
| Alertmanager | `prom/alertmanager:v0.35.0` | `127.0.0.1:9093`(VM 안에서만) | 알람을 묶어 Discord로 보낸다 |
| Grafana | `grafana/grafana:12.4.15` | `http://192.168.0.211:3000`(교육장 안, 로그인 필요) | 대시보드 「CoreBank 계정계 개요」(거래·배치·JVM·DB) |

- 세 컨테이너 모두 `--network host`다. 계정계 API는 `APP_METRICS_ALLOWED_IP=127.0.0.1`에서 온 요청에만 지표를 내주고(`/api/v1/actuator/prometheus`), Nginx는 `/api/v1/actuator`(health 제외)를 외부에 막고 있어서 지표는 VM 밖으로 나가지 않는다.
- Grafana만 교육장 네트워크에 열린다(같은 교육장의 다른 사람이 접속 화면까지는 볼 수 있다). 익명 접속과 회원가입은 끄고 관리자 비밀번호를 파일로 준다. 교육장 밖에서 보려면 별도 경로가 필요한데 이 문서의 범위가 아니다.
- 지표 이름과 라벨: `transfer_count_total{result=SUCCESS|ERROR|EXCEPTION|HELD|PROCESSING|TIMEOUT, replayed}`, `transfer_duration_seconds_*`, `cob_step_count_total{status=SUCCESS|FAILURE|HALTED, step}`, `cob_step_duration_seconds_*`, `notification_publication_abandoned`, `hikaricp_connections_*`, `jvm_*`, `http_server_requests_seconds_*`.

## 2. 설치 (VM, 교육장 안)

1. 맥에서 파일을 올린다(저장소 루트에서).
   ```bash
   scp -r docker/monitoring student@192.168.0.211:monitoring
   ```
2. VM에서 설정 폴더를 만든다.
   ```bash
   sudo mkdir -p /etc/corebank-monitoring && sudo cp -r ~/monitoring/. /etc/corebank-monitoring/ && sudo chown -R root:root /etc/corebank-monitoring && sudo chmod -R go-w /etc/corebank-monitoring && ls /etc/corebank-monitoring
   ```
3. 비밀 파일 두 개를 만든다(값은 화면에 남기지 않는다). Grafana 관리자 비밀번호는 무작위로 만들고, 읽을 때만 `sudo cat`으로 확인한다.
   ```bash
   sudo sh -c 'umask 077; openssl rand -base64 18 > /etc/corebank-monitoring/grafana_admin_password' && sudo chown root:root /etc/corebank-monitoring/grafana_admin_password && sudo chmod 640 /etc/corebank-monitoring/grafana_admin_password
   ```
   Grafana 컨테이너의 사용자는 `uid 472`이지만 **그룹은 `0`(root)**이라 파일 그룹이 root여야 읽는다. 그룹을 `472`로 두면 읽기에 실패하고 Grafana는 오류 없이 기본 비밀번호(`admin`)로 만들어진다(11/16 실제로 겪음). 이미 기동한 뒤라면 파일 그룹을 고치고 `sudo docker exec corebank-grafana sh -c 'grafana cli admin reset-admin-password "$(cat /etc/grafana-secrets/admin_password)"'`로 초기화한다(관리자 비밀번호 설정은 처음 기동할 때만 적용된다).
   Discord 웹훅 주소는 `bash`에서 입력한다(입력이 보이지 않는다). 컨테이너 사용자(`nobody`, 65534)가 읽을 수 있게 그룹만 맞춘다.
   ```bash
   read -rsp "Discord 웹훅 주소: " U; echo; printf '%s\n' "$U" | sudo tee /etc/corebank-monitoring/alertmanager/discord_webhook >/dev/null; unset U; sudo chown root:65534 /etc/corebank-monitoring/alertmanager/discord_webhook && sudo chmod 640 /etc/corebank-monitoring/alertmanager/discord_webhook
   ```
4. 컨테이너 세 개를 순서대로 띄운다(한 줄씩).
   ```bash
   sudo docker run -d --name corebank-prometheus --network host --restart unless-stopped --memory 512m -v /etc/corebank-monitoring/prometheus:/etc/prometheus:ro -v corebank-prometheus-data:/prometheus prom/prometheus:v3.15.1 --config.file=/etc/prometheus/prometheus.yml --storage.tsdb.retention.time=7d --web.listen-address=127.0.0.1:9090 --web.enable-lifecycle
   ```
   ```bash
   sudo docker run -d --name corebank-alertmanager --network host --restart unless-stopped --memory 128m -v /etc/corebank-monitoring/alertmanager:/etc/alertmanager:ro -v corebank-alertmanager-data:/alertmanager prom/alertmanager:v0.35.0 --config.file=/etc/alertmanager/alertmanager.yml --web.listen-address=127.0.0.1:9093 --cluster.listen-address=
   ```
   ```bash
   sudo docker run -d --name corebank-grafana --network host --restart unless-stopped --memory 512m -e GF_SERVER_HTTP_ADDR=192.168.0.211 -e GF_SECURITY_ADMIN_PASSWORD__FILE=/etc/grafana-secrets/admin_password -e GF_USERS_ALLOW_SIGN_UP=false -e GF_AUTH_ANONYMOUS_ENABLED=false -e GF_ANALYTICS_REPORTING_ENABLED=false -e GF_ANALYTICS_CHECK_FOR_UPDATES=false -v /etc/corebank-monitoring/grafana/provisioning:/etc/grafana/provisioning:ro -v /etc/corebank-monitoring/grafana/dashboards:/var/lib/grafana/dashboards:ro -v /etc/corebank-monitoring/grafana_admin_password:/etc/grafana-secrets/admin_password:ro -v corebank-grafana-data:/var/lib/grafana grafana/grafana:12.4.15
   ```
5. 확인한다.
   ```bash
   curl -s http://127.0.0.1:9090/api/v1/targets | python3 -c "import sys,json;[print(t['labels']['job'],t['health']) for t in json.load(sys.stdin)['data']['activeTargets']]"; curl -s -o /dev/null -w 'grafana=%{http_code}\n' http://192.168.0.211:3000/api/health; curl -s -o /dev/null -w 'alertmanager=%{http_code}\n' http://127.0.0.1:9093/-/ready
   ```
   `corebank-server up`, `prometheus up`, `grafana=200`, `alertmanager=200`이어야 한다. 브라우저에서 `http://192.168.0.211:3000`에 `admin`과 위 비밀번호로 로그인하면 `CoreBank` 폴더에 대시보드가 있다.

- 설정을 바꾼 뒤에는 컨테이너를 다시 만들지 않고 Prometheus는 `curl -X POST http://127.0.0.1:9090/-/reload`, Alertmanager는 `curl -X POST http://127.0.0.1:9093/-/reload`로 다시 읽힌다(Grafana 대시보드는 파일을 바꾸고 잠시 기다리면 반영된다).
- 서버 컨테이너(`corebank-server`)를 다시 만들어도 Prometheus는 15초 안에 다시 수집한다. 서버가 잠시 내려가면 `CorebankServerDown`이 1분 뒤에 울린다.

## 3. 알람과 대응

알람은 `docker/monitoring/prometheus/alerts.yml`에 있고 `severity`가 `critical`이면 바로 확인한다. 아래 대응의 DB 조회는 읽기 전용이다(비밀번호를 명령줄에 값으로 쓰지 않고 `export MYSQL_PWD="$DB_PASSWORD"` 뒤 `docker exec -e MYSQL_PWD`로 넘긴다).

| 알람 | 심각도 | 조건 |
|---|---|---|
| CorebankServerDown | critical | 지표 수집이 1분 넘게 실패 |
| TransferTimeout | critical | 처리 불명(`TIMEOUT`) 이체가 10분 안에 1건 이상 |
| CobStepFailed | critical | COB 단계가 1시간 안에 `FAILURE` 또는 `HALTED` |
| Http5xxBurst | warning | 5분 안에 5xx 응답 5건 이상 |
| TransferFailures | warning | 5분 안에 이체 `ERROR`·`EXCEPTION` 3건 이상 |
| TransferLatencyHigh | warning | 이체 p95가 2초 초과로 5분 지속(요청이 있을 때만) |
| HikariPoolSaturated | warning | DB 연결 풀 사용률 80% 초과가 2분 지속 |
| HeapUsageHigh | warning | JVM Old Gen 사용률 90% 초과가 10분 지속 |
| NotificationPublicationAbandoned | warning | 포기된 알림 발행이 1분 넘게 0 초과 |

### CorebankServerDown

1. 컨테이너 상태와 마지막 로그를 본다: `sudo docker ps -a --filter name=corebank-server --format '{{.Names}} {{.Status}}'`, `sudo docker logs --tail 40 corebank-server 2>&1 | cut -c1-200`.
2. 멈췄고 로그에 환경 변수 오류가 없으면 `sudo docker start corebank-server`(환경 파일은 그대로 다시 읽히지 않으므로 값을 바꿨다면 [VM 런북 §4](vm_account_api_runbook.md)대로 지우고 다시 만든다).
3. 헬스: `curl -s http://127.0.0.1:8080/api/v1/actuator/health`.
4. COB 시간대(23:20~00:20)라면 COB 중단 여부를 `batch_execution_history`로 먼저 확인한다.

### Http5xxBurst

1. 오류 로그를 본다: `sudo docker logs --since 10m corebank-server 2>&1 | grep -E "ERROR|Exception" | cut -c1-200 | tail -20`.
2. 같은 시각에 `HikariPoolSaturated`나 `CorebankServerDown`이 울렸는지 본다. DB 연결 문제면 그쪽 대응을 따른다.

### HikariPoolSaturated

1. 느린 쿼리·락 대기를 본다(읽기 전용): `SHOW FULL PROCESSLIST`에서 오래 걸리는 쿼리와 `Waiting for`를 찾는다.
2. 같은 계좌에 이체가 몰리면(핫스팟) 락 대기로 풀이 찬다(PH-56 결과 [ph56_results.md](ph56_results.md)). 부하를 줄이거나 기다린다.

### HeapUsageHigh

1. `sudo docker stats --no-stream corebank-server`로 컨테이너 메모리(한도 2GB)를 본다.
2. 계속 오르면(누수 의심) 대시보드의 힙 그래프에서 증가 추세를 확인하고 서버 컨테이너 재생성을 검토한다. COB 시간대에는 하지 않는다.

### TransferFailures

1. 실패한 이체를 본다(읽기 전용): `SELECT error_code, COUNT(*) FROM transfer WHERE status='ERROR' AND created_at > NOW() - INTERVAL 10 MINUTE GROUP BY error_code`.
2. `ERROR`에는 한도 초과(`LMT…`)나 잔액 부족 같은 **업무 오류도 포함**된다. 시연 중 일부러 낸 오류면 정상이고, 시스템 오류(`EXCEPTION`)가 섞였는지가 중요하다.

### TransferTimeout

1. 타행이체의 모의 대외기관이 살아 있는지 본다: `sudo docker ps --filter name=corebank-mock-external`.
2. 미확정 건을 본다(읽기 전용): `SELECT transfer_id, transaction_number, status FROM transfer WHERE status='TIMEOUT' ORDER BY transfer_id DESC LIMIT 10`. 서버의 조회거래 스케줄러가 결과를 확정한다.

### TransferLatencyHigh

1. 대시보드의 「이체 응답 시간 분위수」와 DB 연결 풀 그래프를 함께 본다.
2. 풀이 차 있으면 `HikariPoolSaturated`와 같은 원인이다. 아니면 모의 대외기관 응답(타행이체)을 의심한다.

### CobStepFailed

1. COB 결과를 본다(읽기 전용): `SELECT job_name, step_name, business_date, status, failure_reason FROM batch_execution_history ORDER BY batch_execution_history_id DESC LIMIT 6`.
2. 영업일과 마감을 확인한다: `SELECT business_date FROM business_date; SELECT base_date, status FROM business_date_cutoff ORDER BY base_date DESC LIMIT 3`.
3. 대사가 불일치로 멈춘 것(`HALTED`)이면 다음 밤도 멈추니 원인(원장·잔액 불일치)을 먼저 찾는다.

### NotificationPublicationAbandoned

1. 포기된 이벤트를 본다(읽기 전용): `SELECT LISTENER_ID, PUBLICATION_DATE, COMPLETION_ATTEMPTS FROM EVENT_PUBLICATION WHERE COMPLETION_DATE IS NULL ORDER BY PUBLICATION_DATE DESC LIMIT 10`.
2. 고객 알림이 빠졌을 수 있다. 서버의 재제출 스케줄러가 처리하는지 지켜보고, 남으면 원인(DB 일시 장애 등)을 확인한다.

## 4. 시간 측정

완료 조건은 "알람 발생부터 인지까지 시간을 측정할 수 있다"이다. 시험 규칙 `MonitoringTestAlert`(항상 참, `for` 없음)를 켠 시각부터 **Alertmanager가 Discord로 전송을 마친 시각**까지를 재고, Discord에서 메시지가 뜬 시각을 눈으로 확인한다.

1. Discord 알림 채널을 열어 둔다.
2. VM에서 아래 한 줄을 실행한다. 시작 전 Discord 전송 횟수(`alertmanager_notifications_total`)를 저장하고, 시험 규칙을 켠 뒤 **횟수가 저장한 값에서 바뀌고 Alertmanager에 `MonitoringTestAlert`가 올라올 때까지** 0.5초마다 확인해 걸린 시간을 보여 준다(`bash`). 누적 횟수라서 저장한 값과 비교하지 않으면 이전 전송 때문에 바로 끝난다. 횟수는 알림 이름별이 아니라서 다른 알람의 전송과 구별하지 못한다. 그래서 알람 존재를 함께 보고, 최종 확인은 3번의 Discord 메시지로 한다.
   ```bash
   m() { curl -s http://127.0.0.1:9093/metrics | grep -E '^alertmanager_notifications_total\{[^}]*integration="discord"' | awk '{print $2}'; }; c0=$(m); t0=$(date +%s.%N); sudo cp /etc/corebank-monitoring/prometheus/alerts-test.yml /etc/corebank-monitoring/prometheus/extra/ && curl -s -X POST http://127.0.0.1:9090/-/reload >/dev/null; until [ "$(m)" != "$c0" ] && curl -s http://127.0.0.1:9093/api/v2/alerts | grep -q MonitoringTestAlert; do sleep 0.5; done; t2=$(date +%s.%N); python3 -c "print('규칙 적용 → Alertmanager가 Discord로 전송 완료: %.1f초 (전송 횟수 %s → %s)' % ($t2 - $t0, '$c0', '$(m)'))"
   ```
3. Discord에 알림이 도착한 시각(메시지 시각)을 읽는다. 전송 실패가 없는지 본다.
   ```bash
   curl -s http://127.0.0.1:9093/metrics | grep -E '^alertmanager_notifications_(total|failed_total)\{[^}]*integration="discord"' | cut -c1-120
   ```
4. **시험 규칙을 반드시 끈다**(안 끄면 계속 울린다). 끄면 해제 알림이 온다(11/16 실측 141초).
   ```bash
   sudo rm /etc/corebank-monitoring/prometheus/extra/alerts-test.yml && curl -s -X POST http://127.0.0.1:9090/-/reload && echo cleaned
   ```
   재측정은 Discord에 **해제 알림이 온 뒤**에 한다(해제 알림이 전송 횟수를 올려 새 알림으로 오인된다).

**측정 결과 (2026-11-16)**

| 구간 | 시간 | 근거 |
|---|---|---|
| 규칙 적용 → Alertmanager가 Discord로 전송 완료 | **20.3초** | 위 2번 출력 |
| Discord 메시지 표시 | 오후 4:42(분 단위) | Discord 화면 |
| 규칙 적용 → Alertmanager가 Discord로 전송 완료, 같은 프로세스 2회 연속(저녁 재측정) | **22.9초, 17.8초** | 위 2번 출력(전송 횟수 2→3, 4→5) |
| Discord 메시지 표시(재측정 2회) | 오후 6:03, 6:11(분 단위) | Discord 화면 |
| 시험 규칙 끈 뒤 해제 알림까지(횟수 3→4) | **141초** | 횟수 변화 대기 출력, Discord `[RESOLVED]` |

- 이론상 최대는 규칙 평가 주기 15초 + Alertmanager 묶음 대기(`group_wait`) 10초 + 전송으로 25초 안팎이고, 측정값 20.3초는 그 안이다.
- 알림 본문(`[FIRING:1] MonitoringTestAlert (demo-vm warning)`)에 라벨, 요약, `runbook_url`이 들어 있고 `runbook_url`이 이 문서의 해당 절로 연결됨을 확인했다.
- 실제 장애 알람은 `for`(예: `CorebankServerDown` 1분)가 더해진 뒤 올라오므로 인지 시간은 `for` + 위 시간이다.

### 이체 지표 확인 (2026-11-16)

이체 전 숫자는 기록하지 못했고 Prometheus의 `increase(transfer_count_total[10m])`로 확인했다. 1,000원 성공 이체 1건 뒤 `SUCCESS`(replayed=false)가 1.0 늘었고 다른 결과는 0이었다. 대시보드 「이체 성공 (최근 1시간)」도 1로 올랐고 계좌 잔액이 이체 금액만큼 변했다(출금 87,000→86,000, 입금 100,000→101,000).

- **확인하지 못한 것**: 실패 경로(`ERROR` 등). 화면이 잔액 초과를 서버에 보내기 전에 막아서 만들지 못했고, 계좌비밀번호·OTP를 일부러 틀리는 방법은 잠금 위험 때문에 쓰지 않았다.
- 지표가 없을 때 5xx 패널은 `0`, 활성 알람 표는 「알람 없음」으로 보이게 했다(5xx가 한 번도 없으면 지표 자체가 생기지 않는다).

## 5. 한계와 끄기

- Discord 알림 맨 아래 `Source` 링크는 `http://vm-server:9090/...`인데 Prometheus가 VM 안(`127.0.0.1`)에서만 열려 있어 클릭해도 열리지 않는다(알림 내용에는 영향 없음).
- **교육장 안에서만** 대시보드를 볼 수 있다. VM 접속도 교육장에서만 한다(3-Tier 철거로 SSM 세션이 없다).
- Grafana는 HTTP(`http://192.168.0.211:3000`)라 로그인과 세션 통신이 암호화되지 않는다. 교육장 공유 LAN을 신뢰 범위로 보고 LAN에만 바인딩한 시연 전용 구성이다. 교육장 밖에서 보거나 시연 뒤에도 계속 쓰려면 HTTPS 프록시(Nginx) 뒤에 두거나 `127.0.0.1` 바인딩과 SSH 터널로 바꾼다(이 전환은 시험하지 않았다).
- 수집 대상은 계정계 API 하나다. 정보계 API(`info-api`), MySQL, VM 자원(CPU·디스크)은 수집하지 않는다.
- Prometheus 데이터는 7일 보관하고 VM 디스크(`corebank-prometheus-data` 볼륨)에 쌓인다.
- 알람은 시연 환경 기준의 값이라 운영 임계값이 아니다(이체 지연 2초, 풀 80% 등은 PH-56 결과와 시연 부하를 보고 정했다).
- 끄기: `sudo docker stop corebank-grafana corebank-alertmanager corebank-prometheus`(데이터는 볼륨에 남는다). 완전히 지우려면 `rm` 뒤 볼륨(`corebank-*-data`)과 `/etc/corebank-monitoring`을 지운다.

## 6. 관련 문서

- [VM 계정계 API 런북](vm_account_api_runbook.md)
- [PH-56 WAS 스케일아웃 측정 결과](ph56_results.md)
- 이슈 #619, 지표 노출을 허용 IP로 제한한 PR #917
