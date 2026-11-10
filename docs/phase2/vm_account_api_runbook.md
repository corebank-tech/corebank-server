# VM 계정계 API 기동 · 정지 런북 (#849)

> 11/18 시연과 [DR 전환](ph54_onprem_dr_runbook.md) 때 온프레미스 VM(`192.168.0.211`)에서 계정계 API를 켜고 끄는 절차다.
> 승격과 복제 정지는 P2([DR 런북](ph54_onprem_dr_runbook.md) §6)가 하고, 이 문서는 그 절차가 부르는 **기동 · 정지 한 칸**만 다룬다.
> 작성 2026-11-10. **격리 DB 위 기동은 확인했고, 승격된 DB로의 기동은 아직 해 보지 않았다.** 확인한 것과 하지 않은 것을 §7에 나눠 적는다.

## 1. 전제

- 이미지는 **릴리스 4 `corebank/corebank-server:e6dfafe4dfe64bb3fb94a82382e7a93d72ed3284`로 고정**한다(시험한 것과 시연하는 것을 같게 하려는 것이다). VM에 이미 받아 두었다.
- **실시간 레플리카에는 승격 전에 API를 붙이지 않는다.** 앱은 기동할 때 Flyway와 세션 저장소가 DB에 쓰는데, 읽기 전용 레플리카에 붙으면 실패하고 쓰기가 되는 상태로 붙이면 RDS와 GTID가 갈라진다. 승격 전 시험은 쓰기가 되는 격리 DB(`corebank-drtest-mysql`)에서만 한다.
- 복제 중인 VM은 재부팅과 도커 재시작을 하지 않는다. 새 컨테이너를 띄우는 것은 도커 재시작이 아니다.
- 환경 파일은 값을 이 문서와 저장소, 채팅, 이슈에 쓰지 않는다.

## 2. 환경 파일

시험용은 `/etc/corebank-drtest/app.env`(권한 600, root). 변수 **이름**은 아래와 같다.

| 변수 | 비고 |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DB_HOST` | 격리 DB는 컨테이너 이름, 승격 뒤는 `127.0.0.1`(§5) |
| `DB_USER` `DB_PASSWORD` `FLYWAY_USER` `FLYWAY_PASSWORD` | 격리 DB와 승격 DB(RDS에서 복제된 계정)가 다를 수 있다 |
| `DB_POOL_SIZE` | 시험은 3 |
| `APP_OTP_CODE_HASH_ALGORITHM` `APP_OTP_CODE_HASH_KEY_BASE64` | 키는 VM에서 직접 생성한다 |
| `APP_LOGIN_ID_HMAC_KEY_BASE64` | 릴리스 4부터 필수. 없으면 기동하지 않는다 |
| `APP_AUDIT_ARCHIVE_STORAGE` | `local`. 감사 로그를 AWS 보관함이 아니라 서버 안에 저장한다 |
| `APP_LEDGER_RECONCILIATION_FULL_SCAN_CHUNK_TIMEOUT_SECONDS` | `1800`. 코드 기본값은 60초다 |

기동할 때 덧붙이는 값(환경 파일에 넣지 않는다): `APP_INTERBANK_HOST=192.168.0.211`(VM 모의 대외기관, 포트 9300), 승격 뒤에는 `SERVER_ADDRESS=127.0.0.1`과 `APP_METRICS_ALLOWED_IP`(§5).

DB 접속 URL은 포트가 `3306`으로 고정이고 `sslMode=REQUIRED`다. 레플리카는 TLS 접속을 받는다(`--ssl-mode=REQUIRED`로 확인, `TLSv1.2,TLSv1.3`).

## 3. 격리 DB 위 기동 (승격 전 시험)

```bash
sudo docker run -d --name corebank-drtest-api --network corebank-drtest-net --cpus 1 --memory 2g \
  -p 127.0.0.1:8080:8080 --env-file /etc/corebank-drtest/app.env \
  -e APP_INTERBANK_HOST=192.168.0.211 \
  corebank/corebank-server:e6dfafe4dfe64bb3fb94a82382e7a93d72ed3284
```

- 앱은 `/api/v1` 아래에 있고, 기동까지 약 20~60초 걸린다.
- 이 컨테이너를 밤에 켜 두지 않는다. 23:30 COB와 00:30 감사 적재가 격리 DB에서 돌 수 있다. 시험 때는 `sudo systemd-run --on-active=90m --unit=drtest-api-autostop docker stop corebank-drtest-api`로 자동 종료를 걸어 두었다.

## 4. 헬스체크와 정지

```bash
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8080/api/v1/actuator/health   # 200
sudo docker logs corebank-drtest-api 2>&1 | grep -E "ERROR|환경변수가 없습니다|APPLICATION FAILED" | cut -c1-200 | head
```

```bash
sudo docker stop corebank-drtest-api && sudo docker rm corebank-drtest-api
```

- 환경 파일, 이미지, DB는 지우지 않는다. 값을 바꾼 뒤에는 `restart`가 아니라 지우고 다시 만든다(`docker restart`는 환경 파일을 다시 읽지 않는다).

## 5. 승격 뒤 기동 (계획, 시험 전)

레플리카는 기본 `bridge` 네트워크에서 호스트 `127.0.0.1:3306`으로만 열려 있고, 정보계 API(`info-api`)가 이미 호스트 네트워크로 붙는다. 계정계 API도 같은 방식으로 붙인다. 레플리카 컨테이너는 건드리지 않는다.

```bash
sudo docker run -d --name corebank-server --network host --cpus 1 --memory 2g \
  --env-file <승격 DB용 환경 파일> \
  -e SERVER_ADDRESS=127.0.0.1 -e APP_INTERBANK_HOST=192.168.0.211 -e APP_METRICS_ALLOWED_IP=127.0.0.1 \
  corebank/corebank-server:e6dfafe4dfe64bb3fb94a82382e7a93d72ed3284
```

- `DB_HOST=127.0.0.1`, DB 계정은 승격 뒤 P2가 확인한 `app_user` · `flyway_user` 값을 VM에서 직접 입력한다.
- `SERVER_ADDRESS=127.0.0.1`로 VM 안에서만 받게 한다. 호스트 네트워크로 띄우면 이 값이 없을 때 교육장 전체에 열린다.
- `APP_METRICS_ALLOWED_IP=127.0.0.1`은 Prometheus가 호스트에서 `127.0.0.1:8080`을 부른다는 가정이다. 기본값 `192.168.0.211`은 VM 안의 요청에서 거부된다(§7).
- 승격 전 시험 때와 달리 `--network host`라 `-p`를 쓰지 않는다.

## 6. 전수 대사 리허설 (PH-38 실행기)

격리 DB에서 전수 대사를 돌려 시간과 불일치 수를 로그에 남기고 스스로 종료한다. 스케줄러가 같이 돌지 않도록 `phase2-seed` 프로필을 붙이고, 시드 실행 플래그는 켜지 않는다.

```bash
sudo docker run -d --name corebank-drtest-recon --network corebank-drtest-net --cpus 1 --memory 2g \
  --env-file /etc/corebank-drtest/app.env \
  -e SPRING_PROFILES_ACTIVE=prod,ph38-baseline,phase2-seed \
  -e APP_PH38_BASELINE_EXECUTE=true -e APP_PH38_BASELINE_ROUNDS=2 -e APP_PH38_BASELINE_WARMUP_ROUNDS=1 \
  corebank/corebank-server:e6dfafe4dfe64bb3fb94a82382e7a93d72ed3284
```

- 끝나면 `sudo docker inspect -f '{{.State.Status}} exit={{.State.ExitCode}}' corebank-drtest-recon`이 `exited exit=0`이어야 한다. `--rm`을 쓰지 않는다. 실행 전후로 `batch_execution_lock`의 `currently_running`이 모두 `0`인지 본다.
- 11/10 결과: 불일치 0건, 예열 회차 559초(캐시가 비어 있을 때), 측정 회차 25초. 승격 직후 처음 도는 대사는 10분 가까이 걸릴 수 있다.
- 주의: 이 프로필에서도 격리 DB의 `IDEMPOTENCY_KEY_CLEANUP` 락이 갱신됐다. 원인은 확인하지 못했으니 이 프로필로 COB를 막는다고 믿지 않는다.

## 7. 확인한 것과 하지 않은 것

**확인함 (2026-11-10, 격리 DB)**
- 릴리스 4 이미지가 기동하고 헬스 200, 로그인 키 · OTP 키 관련 오류 없음.
- `APP_INTERBANK_HOST=192.168.0.211`로 기동, 컨테이너에서 `192.168.0.211:9300` · `:8081`에 접속됨.
- 레플리카 TLS 접속(`sslMode=REQUIRED` 조건).
- VM 안에서 `/actuator/prometheus`를 부르면 `401`로 거부된다(컨테이너 안의 앱은 호스트 요청을 도커 게이트웨이 주소로 본다).
- 전수 대사 리허설(§6).

**하지 않음**
- 승격된 DB로의 기동, 호스트 네트워크 방식(§5)의 DB 접속. 포트가 3306으로 고정이라 격리 DB(`127.0.0.1:3310`)로는 시험하지 못했다.
- 로그인 · 이체 · 해지 화면 동작(#849 완료 조건). 접속 경로(Nginx · 인증서 · hosts)가 있어야 화면으로 눌러 볼 수 있다. 해지는 설정 스위치(`APP_SUBSCRIPTION_EARLYTERMINATION_ENABLED`)가 따로 있는 것으로 알고 VM 환경 파일에는 이 변수가 없다. 스위치 기본값은 확인하지 못했으니 시험 전에 확인한다.
- 정보계 API 호출. `APP_INFO_API_BASE_URL`을 넣으면 `APP_INFO_API_SECRET`도 필요한데 값의 위치를 확인하지 못했다.
- `APP_METRICS_ALLOWED_IP=127.0.0.1`로 `/actuator/prometheus`가 200이 되는지.
- Nginx · 인증서 · 발표 노트북 hosts를 통한 접속.

## 8. 관련 문서

- [PH-54 DR 런북](ph54_onprem_dr_runbook.md): 승격, 쓰기 차단, GTID 동기화
- [VPN · VM 런북](ph100_vpn_vm_runbook.md): 터널 복구와 복제 확인
- [모의 대외기관](mock_external_server.md): VM 9300 · 9301
