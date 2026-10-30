# PH-76 정보계 ETL 운영 런북 (온프레미스 VM)

> `info-etl.jar`를 부트캠프 VM에 올려 마트를 채우고 매일 자동 적재하는 순서를 적는다(#691).
> 이 문서는 절차만 적는다. 비밀번호·키 값은 어디에도 적지 않고 `<...>`로 둔다.
> 스키마와 적재 규칙은 [info_mart.md](info_mart.md), 로컬 측정은 [ph76_etl_first_load.md](ph76_etl_first_load.md)를 본다.

## 0. 전제와 미정 값

- VM: Ubuntu 22.04, 2코어 · RAM 약 14GB · 디스크 55GB(약 46GB 여유), Docker 설치, `student` 계정으로 `sudo` 가능(비밀번호 입력). 설치 패키지 목록에 Java가 없어서 ETL은 컨테이너로 실행한다(`java -version`으로 없음을 확인했다).
- 레플리카는 10/29 저녁 RDS 전체 덤프를 적재하고 복제를 시작했다(PH-70-②). 10/30 아침에 지연 0초 · 오류 없음, RDS와 GTID `...:1-1083`에서 주요 4개 테이블 건수가 일치했고, `read_only` · `super_read_only`를 켰다(`SET PERSIST`).
- VPN 터널은 VM 재부팅 뒤 약 1분 안에 자동 복구되고(`vti1`, `10.30.0.0/16`), VM에서 새 RDS 3306 연결이 된다(10/29 확인). 터널 서비스(`strongswan`, `vpn-vti`)는 건드리지 않는다.
- 이 VM은 우리 팀 전용이다(강사님이 팀마다 IP를 따로 준다). 그래도 포트를 쓰기 전에 `ss -ltn`으로 비어 있는지 본다.
- 명령은 한 줄씩 붙여 넣는다. `\`로 줄을 나눈 여러 줄 명령을 그대로 붙이면 첫 줄만 실행되는 일이 있었다.

| 값 | 상태 |
| --- | --- |
| 경계일 `ETL_CUTOFF_BOUNDARY_DATE` | **2026-10-26.** 운영 `business_date_cutoff`의 가장 이른 `CLOSED` 행(10/27 아침 조회). 적재 직전에 한 번 더 확인한다 |
| VM 시간대 | **KST(`Asia/Seoul`)** 확인함. §7의 시각을 그대로 쓴다 |
| 초기 데이터 방식 | 완료(10/29). RDS 덤프(`--single-transaction --set-gtid-purged=OFF`) 적재 → `gtid_purged` 입력 → `CHANGE REPLICATION SOURCE`(`SOURCE_AUTO_POSITION=1`, SSL) → `START REPLICA`. 복제 필터는 `mysql.rds%` 무시 |

## 1. 구성

- 레플리카 역할 MySQL(원본 복사본): 포트 3306. 컨테이너 `info-replica-mysql`, 복제 옵션은 P2가 정한다(§2-1).
- 마트 MySQL(`info_mart`): 포트 3307. 컨테이너 `info-mart-mysql`. `super_read_only`가 인스턴스 전체에 걸리므로 레플리카와 반드시 다른 인스턴스다.
- ETL: 일회성 컨테이너(`--rm`)가 jar를 실행하고 종료한다. 서비스로 상주하지 않는다.
- 두 MySQL은 `127.0.0.1`에만 연다. 외부에는 정보계 API(#704)만 나간다. 정보계 API는 컨테이너 `info-api`로 이미 떠 있다(배포 절차는 #776에서 따로 적는다). VM 안에서는 `asOf=null` 응답까지 확인했고, WAS → 정보계 API 호출은 #804(PR #805)와 11/3 `main` 릴리스 뒤에야 확인할 수 있다.
- 마트 · 레플리카 · `info-api` 컨테이너는 모두 `--restart unless-stopped`로 돈다.

## 2. 마트 MySQL 구성

```bash
ss -ltn | grep -E ':(3306|3307)\b'   # 비어 있어야 한다
sudo mkdir -p /var/lib/info-mart
docker run -d --name info-mart-mysql --restart unless-stopped \
  -e MYSQL_ROOT_PASSWORD_FILE=/run/secrets/root_pw \
  -v /var/lib/info-mart:/var/lib/mysql \
  -v /etc/info-etl/root.pw:/run/secrets/root_pw:ro \
  -p 127.0.0.1:3307:3306 \
  mysql:8.4 --innodb-buffer-pool-size=1G --max-allowed-packet=256M --character-set-server=utf8mb4
```

- 루트 비밀번호 파일 `/etc/info-etl/root.pw`는 먼저 만든다(권한 600). §3의 비밀 파일 생성 명령으로 만들고 화면에 출력하지 않는다.
- 컨테이너가 처음 뜰 때는 임시 서버를 먼저 띄웠다가 다시 시작한다. 로그에 `port: 3306  MySQL Community Server`가 있는 `ready for connections` 줄이 나온 뒤에 접속한다(`port: 0`인 줄은 임시 서버다).
- 마트는 로컬 측정에서 약 1.3GB였다. 버퍼 풀 1GB로 시작하고 VM의 다른 컨테이너 사용량을 보며 조정한다.
- 스키마 적용(DB 이름은 `info_mart`). 스키마 파일은 노트북에만 있으므로 먼저 VM으로 올린다. 파일을 올리는 것과 DB에 적용하는 것은 별개 단계다.

```bash
# 노트북에서
scp src/infoEtl/resources/mart-schema.sql student@<VM>:/tmp/mart-schema.sql
```

```bash
# VM에서
docker exec -i info-mart-mysql sh -c 'mysql -uroot -p"$(cat /run/secrets/root_pw)" -e "CREATE DATABASE IF NOT EXISTS info_mart DEFAULT CHARACTER SET utf8mb4"'
docker exec -i info-mart-mysql sh -c 'mysql -uroot -p"$(cat /run/secrets/root_pw)" info_mart' < /tmp/mart-schema.sql
docker exec -i info-mart-mysql sh -c 'mysql -uroot -p"$(cat /run/secrets/root_pw)" info_mart -e "SHOW TABLES"'   # 테이블 3개가 나와야 한다
```

### 2-1. 레플리카 역할 MySQL (3306)

서버만 띄운다. 복제 옵션은 P2가 정한 값에 맞춘다. 현재(10/29)는 `gtid_mode=ON`, `enforce_gtid_consistency=ON`, `server_id=101`이고 `SET PERSIST`로 영구 저장했다. `super_read_only`는 초기 적재가 끝난 뒤에 켠다. 먼저 켜면 덤프를 넣지 못한다. 그 밖의 옵션을 바꿀 때는 컨테이너를 지우고 같은 데이터 폴더(`/var/lib/info-replica`)로 다시 띄운다.

- 새 RDS는 `gtid-mode=ON`, `binlog_format=ROW`, `require_secure_transport=1`이다. 복제 연결에는 SSL 옵션이 필요하다.
- 복제가 도는 동안에는 VM 재부팅과 도커 재시작을 하지 않는다.
- 복제가 붙은 뒤 병렬 복제이면 `replica_preserve_commit_order=ON`을 확인한다.

```bash
umask 077; openssl rand -base64 24 | tr -d '\n' > /etc/info-etl/replica-root.pw
sudo mkdir -p /var/lib/info-replica
docker run -d --name info-replica-mysql --restart unless-stopped -e MYSQL_ROOT_PASSWORD_FILE=/run/secrets/root_pw -v /var/lib/info-replica:/var/lib/mysql -v /etc/info-etl/replica-root.pw:/run/secrets/root_pw:ro -p 127.0.0.1:3306:3306 mysql:8.4 --innodb-buffer-pool-size=2G --max-allowed-packet=256M --character-set-server=utf8mb4
```

- `docker run`은 한 줄로 붙여 넣는다.
- 버퍼 풀 2GB는 시작값이다(RAM 약 14GB 중 마트 컨테이너가 약 0.5GB를 쓰는 상태에서 정했다). 정보계 API와 모니터링이 올라오면 `docker stats`로 보고 조정한다.
- 기동 확인은 마트와 같은 방식이다(`docker ps`, 로그의 `port: 3306` 줄, `ss -ltn`).
- 레플리카에 원본 테이블이 생기기 전에는 `etl_replica_reader`의 `GRANT`가 실패한다. 테이블이 생긴 뒤에 §3의 레플리카용 SQL을 적용한다.

## 3. 계정 3개

`src/infoEtl/resources/etl-grants.sql`의 `CHANGE_ME`를 그대로 쓰지 않는다. 비밀번호는 만들어서 파일에만 두고 화면에 출력하지 않는다.

- `etl_replica_reader`: 레플리카 인스턴스에서 적재가 읽는 테이블에만 `SELECT`. 개인정보 테이블(`customer` 등)은 주지 않는다.
- `etl_mart_writer`: 마트 인스턴스에서 `info_mart.*`에 `SELECT, INSERT, UPDATE, DELETE`. DDL 권한은 없다.
- `info_api_reader`: 마트 인스턴스에서 `info_mart.*`에 `SELECT`만. 정보계 API(#704)가 쓴다.

파일은 두 인스턴스에 나누어 적용한다. `etl_replica_reader`를 만드는 줄과 그 `GRANT`(minicore) 줄은 레플리카에, `etl_mart_writer`·`info_api_reader` 줄은 마트에 적용한다.

```bash
umask 077
sudo mkdir -p /etc/info-etl && sudo chown student /etc/info-etl
for n in root replica mart api; do openssl rand -base64 24 | tr -d '\n' > /etc/info-etl/$n.pw; done
openssl rand -hex 32 | tr -d '\n' > /etc/info-etl/hmac.key
ls -l /etc/info-etl && wc -c /etc/info-etl/*   # 권한 600, .pw는 32바이트, hmac.key는 64바이트
```

- 파일 이름과 쓰임: `root.pw`는 마트 root, `replica.pw`는 `etl_replica_reader`, `mart.pw`는 `etl_mart_writer`, `api.pw`는 `info_api_reader`, `hmac.key`는 가명 키(§4). 레플리카 root 비밀번호는 `replica-root.pw`(§2-1)로 따로 둔다.

- 만든 비밀번호는 출력하지 않는다. `CHANGE_ME`를 해당 비밀번호로 바꾼 임시 SQL 파일을 두 개(레플리카용, 마트용) 만들어 각 인스턴스에서 실행하고, 임시 파일은 `shred -u`로 지운다.
- 적용 전에 임시 파일에 `CHANGE_ME`가 남지 않았는지 `grep -c CHANGE_ME`로 확인한다(0이어야 한다).
- 비밀번호에 `'`나 `\`가 들어가면 SQL이 깨지므로 `openssl rand -base64`처럼 안전한 문자만 쓴다.
- 레플리카에 `super_read_only`를 켠 뒤에는 `CREATE USER`가 막힌다. 10/30에는 한 세션에서 `SET GLOBAL super_read_only=OFF; SET GLOBAL read_only=OFF; SET sql_log_bin=0;`으로 풀고 `etl_replica_reader`를 만든 뒤 `SET GLOBAL read_only=ON; SET GLOBAL super_read_only=ON;`으로 다시 잠갔다(`--force`로 돌려 중간 실패에도 잠금 문장은 실행). `sql_log_bin=0`이라 레플리카에 자체 GTID가 생기지 않았고(`gtid_executed`가 원본 UUID 하나로만 이어짐), `SHOW GRANTS`로 읽기 7개 테이블만 있는 것을 확인했다.

## 4. 환경변수 파일

`/etc/info-etl/etl.env`(권한 600, 소유자 `student`). 값은 저장소·로그·채팅에 남기지 않는다.

```
REPLICA_JDBC_URL=jdbc:mysql://127.0.0.1:3306/minicore?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=Asia/Seoul&characterEncoding=UTF-8
REPLICA_USER=etl_replica_reader
REPLICA_PASSWORD=<replica.pw 값>
MART_JDBC_URL=jdbc:mysql://127.0.0.1:3307/info_mart?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=Asia/Seoul&characterEncoding=UTF-8
MART_USER=etl_mart_writer
MART_PASSWORD=<mart.pw 값>
HMAC_KEY=<32바이트 이상>
ETL_CUTOFF_BOUNDARY_DATE=<yyyy-MM-dd>
```

- `HMAC_KEY` 값은 §3에서 만든 `/etc/info-etl/hmac.key`의 내용이다(`openssl rand -hex 32`). 키가 바뀌면 같은 계좌의 가명 키가 달라지므로 info_mart.md §3-3의 키 교체 절차를 따라야 한다. 분실하지 않게 팀의 비밀 보관 위치에 따로 둔다.
- `ETL_CUTOFF_BOUNDARY_DATE`는 `--auto`에 필요하다. 없으면 `--auto`는 종료 코드 2로 끝난다. 이 값은 §0의 경계일이 확인된 뒤에 채운다.

## 5. jar 빌드와 전달

```bash
./gradlew infoEtlJar                         # build/info-etl/info-etl.jar
ssh student@<VM> 'sudo mkdir -p /opt/info-etl && sudo chown student /opt/info-etl'
scp build/info-etl/info-etl.jar student@<VM>:/opt/info-etl/info-etl.jar
shasum -a 256 build/info-etl/info-etl.jar    # 노트북의 해시. VM의 `sha256sum /opt/info-etl/info-etl.jar`와 같아야 한다
```

- jar는 `dev`에 합쳐진 최신 코드로 빌드한다. 브랜치 코드로 빌드하면 최신 변경이 빠질 수 있다(작업 브랜치가 `dev`보다 뒤처져 있으면 실제로 그렇다).

실행(공통 형태). 호스트에 Java가 없으므로 컨테이너로 한다.

```bash
docker run --rm --network host -e TZ=Asia/Seoul --env-file /etc/info-etl/etl.env \
  -v /opt/info-etl:/app:ro eclipse-temurin:21-jre \
  java -Xmx2g -jar /app/info-etl.jar <인자>
```

- 종료 코드: 0 성공, 1 적재 실패, 2 인자·설정 오류.
- 로컬 측정에서 JVM 최대 메모리는 `-Xmx2g`였다. VM에서는 다른 컨테이너를 보고 조정한다.

## 6. 실행 순서

1. §2~§5까지 준비한다(경계일 없이 가능).
2. 경계일을 확인한다(운영 DB 조회). 첫 COB 이후에 가능하다.
   ```sql
   SELECT base_date, status, closed_at FROM business_date_cutoff ORDER BY base_date;
   ```
   가장 이른 `CLOSED` 행의 `base_date`가 경계일이다. 값을 `etl.env`에 쓴다.
3. 경계일 이전을 수동으로 한 번 채운다. 원장 첫 거래일은 2026-09-01이다.
   `--from=2026-09-01 --to=<경계일 전날>`
4. `--verify-pii`로 PII 검증을 통과시킨다. 위반이 있으면 중단하고 원인을 찾는다.
5. 이후를 자동으로 돌린다(`--auto`). 아래 타이머를 등록한다.
   각 단계(3·4번)의 소요 시간과 JVM 최대 메모리는 [ph76_etl_first_load.md](ph76_etl_first_load.md) §2에 같은 항목으로 기록한다(#691 최종 VM 재측정).
6. 첫 자동 적재 뒤 마트에서 확인한다.
   ```sql
   SELECT base_date, status, as_of_date, completed_at FROM mart_load_batch ORDER BY completed_at DESC LIMIT 5;
   ```

## 7. 자동 실행 타이머 (systemd)

COB는 23:30에 시작한다. 새 환경의 10/28 밤 대사는 93.3초였고 COB 전체가 시작 약 1분 35초에 끝났다(기존 환경은 1,219.9초). 새벽 시각에 부르면 cutoff가 이미 복제된 뒤다. 시각은 03:30으로 시작하고, 복제 지연을 실측한 뒤 앞당길 수 있는지 본다. VM 시간대는 KST로 확인했다.

`/etc/systemd/system/info-etl.service`

```
[Unit]
Description=정보계 ETL 자동 적재
After=docker.service
Requires=docker.service

[Service]
Type=oneshot
ExecStart=/usr/bin/docker run --rm --network host -e TZ=Asia/Seoul --env-file /etc/info-etl/etl.env -v /opt/info-etl:/app:ro eclipse-temurin:21-jre java -Xmx2g -jar /app/info-etl.jar --auto
```

`/etc/systemd/system/info-etl.timer`

```
[Unit]
Description=정보계 ETL 자동 적재 타이머

[Timer]
OnCalendar=*-*-* 03:30:00
Persistent=true

[Install]
WantedBy=timers.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now info-etl.timer
systemctl list-timers info-etl.timer
journalctl -u info-etl.service -n 50 --no-pager   # 마지막 실행 결과
```

- 같은 날짜 동시 실행은 기준일 락이 막는다. 적재 실패는 soft라 종료 코드 1과 로그로만 드러난다. `systemctl status info-etl.service`에서 실패 여부를 본다.
- 이 타이머는 경계일 설정(`ETL_CUTOFF_BOUNDARY_DATE`)과 수동 적재(§6 3번)가 끝난 뒤에 켠다.
- 10/30에 등록했다(`systemctl list-timers`의 다음 실행 `Sat 2026-10-31 03:30:00 KST`). 등록 직후 서비스를 한 번 시험 실행해 종료 코드 0, `성공 0일, 이미 완료되어 건너뜀 4일`을 확인했다. 이미 적재한 날짜는 건너뛰므로 같은 날을 두 번 불러도 안전하다.
- 컨테이너 시간대는 UTC라서 `-e TZ=Asia/Seoul`이 없으면 로그 시각이 9시간 늦게 찍힌다. 마트에 기록되는 시각은 코드가 `Asia/Seoul`로 고정해 영향이 없다.

## 8. 하지 않는 것

- 레플리카와 같은 MySQL 인스턴스에 마트를 만들지 않는다.
- 비밀번호·`HMAC_KEY`를 저장소, 로그, 명령 기록, 채팅에 남기지 않는다. `etl.env`의 권한은 600이다.
- 마트 DDL 권한을 ETL 계정이나 정보계 API 계정에 주지 않는다.
- 마트(3307)와 레플리카(3306) 컨테이너·포트를 임의로 끄거나 바꾸지 않는다. 레플리카 옵션을 바꿀 때는 P2와 맞춘다.
