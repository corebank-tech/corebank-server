# PH-76 정보계 ETL 운영 런북 (온프레미스 VM)

> `info-etl.jar`를 부트캠프 VM에 올려 마트를 채우고 매일 자동 적재하는 순서를 적는다(#691).
> 이 문서는 절차만 적는다. 비밀번호·키 값은 어디에도 적지 않고 `<...>`로 둔다.
> 스키마와 적재 규칙은 [info_mart.md](info_mart.md), 로컬 측정은 [ph76_etl_first_load.md](ph76_etl_first_load.md)를 본다.

## 0. 전제와 미정 값

- VM: Ubuntu 22.04, 2코어 · RAM 15GB · 디스크 55GB(약 46GB 여유), Docker 설치, `student` 계정으로 `sudo` 가능. 강사님이 알려 준 설치 패키지 목록에 Java가 없어서 ETL은 컨테이너로 실행한다(VM에서 `java -version`으로 한 번 확인한다).
- 레플리카는 10/27 복제 시작(P2 PH-70-②) 뒤에 생긴다. 그 전에는 덤프를 적재한 DB가 레플리카 역할을 한다(Plan B).
- VM은 다른 팀과 공용일 수 있다. 포트를 쓰기 전에 `ss -ltn`으로 비어 있는지 보고, 남의 컨테이너·포트는 건드리지 않는다.

| 값 | 상태 |
| --- | --- |
| 경계일 `ETL_CUTOFF_BOUNDARY_DATE` | 운영 첫 COB(2026-10-26 23:30)가 `business_date_cutoff`에 남기는 첫 `base_date`. 확인 뒤 채운다 |
| VM 시간대 | 미확인. `timedatectl`로 확인하고 §6의 시각을 맞춘다 |
| 덤프 이동 방법 | 미정. 레플리카 복제가 시작되면 필요 없다 |

## 1. 구성

- 레플리카 역할 MySQL(원본 복사본): 포트 3306
- 마트 MySQL(`info_mart`): 포트 3307. `super_read_only`가 인스턴스 전체에 걸리므로 레플리카와 반드시 다른 인스턴스다.
- ETL: 일회성 컨테이너(`--rm`)가 jar를 실행하고 종료한다. 서비스로 상주하지 않는다.
- 두 MySQL은 `127.0.0.1`에만 연다. 외부에는 정보계 API(#704)만 나간다.

## 2. 마트 MySQL 구성

```bash
ss -ltn | grep -E ':(3306|3307)\b'   # 비어 있어야 한다
sudo mkdir -p /var/lib/info-mart
docker run -d --name info-mart-mysql --restart unless-stopped \
  -e MYSQL_ROOT_PASSWORD_FILE=/run/secrets/root_pw \
  -v /var/lib/info-mart:/var/lib/mysql \
  -v /etc/info-etl/mart-root.pw:/run/secrets/root_pw:ro \
  -p 127.0.0.1:3307:3306 \
  mysql:8.4 --innodb-buffer-pool-size=1G --max-allowed-packet=256M --character-set-server=utf8mb4
```

- 루트 비밀번호 파일 `/etc/info-etl/mart-root.pw`는 먼저 만든다(권한 600). 값은 §4에서 만든 방식으로 생성하고 화면에 출력하지 않는다.
- 마트는 로컬 측정에서 약 1.3GB였다. 버퍼 풀 1GB로 시작하고 VM의 다른 컨테이너 사용량을 보며 조정한다.
- 스키마 적용(DB 이름은 `info_mart`):

```bash
docker exec -i info-mart-mysql sh -c 'mysql -uroot -p"$(cat /run/secrets/root_pw)" -e "CREATE DATABASE IF NOT EXISTS info_mart DEFAULT CHARACTER SET utf8mb4"'
docker exec -i info-mart-mysql sh -c 'mysql -uroot -p"$(cat /run/secrets/root_pw)" info_mart' < src/infoEtl/resources/mart-schema.sql
```

## 3. 계정 3개

`src/infoEtl/resources/etl-grants.sql`의 `CHANGE_ME`를 그대로 쓰지 않는다. 비밀번호는 만들어서 파일에만 두고 화면에 출력하지 않는다.

- `etl_replica_reader`: 레플리카 인스턴스에서 적재가 읽는 테이블에만 `SELECT`. 개인정보 테이블(`customer` 등)은 주지 않는다.
- `etl_mart_writer`: 마트 인스턴스에서 `info_mart.*`에 `SELECT, INSERT, UPDATE, DELETE`. DDL 권한은 없다.
- `info_api_reader`: 마트 인스턴스에서 `info_mart.*`에 `SELECT`만. 정보계 API(#704)가 쓴다.

파일은 두 인스턴스에 나누어 적용한다. `etl_replica_reader`를 만드는 줄과 그 `GRANT`(minicore) 줄은 레플리카에, `etl_mart_writer`·`info_api_reader` 줄은 마트에 적용한다.

```bash
umask 077
sudo mkdir -p /etc/info-etl && sudo chown student /etc/info-etl
for n in replica mart api; do openssl rand -base64 24 | tr -d '\n' > /etc/info-etl/$n.pw; done
```

- 만든 비밀번호는 출력하지 않는다. `CHANGE_ME`를 해당 비밀번호로 바꾼 임시 SQL 파일을 두 개(레플리카용, 마트용) 만들어 각 인스턴스에서 실행하고, 임시 파일은 `shred -u`로 지운다.
- 적용 전에 임시 파일에 `CHANGE_ME`가 남지 않았는지 `grep -c CHANGE_ME`로 확인한다(0이어야 한다).
- 비밀번호에 `'`나 `\`가 들어가면 SQL이 깨지므로 `openssl rand -base64`처럼 안전한 문자만 쓴다.

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

- `HMAC_KEY`는 `openssl rand -hex 32`로 만든다. 키가 바뀌면 같은 계좌의 가명 키가 달라지므로 info_mart.md §3-3의 키 교체 절차를 따라야 한다. 분실하지 않게 팀의 비밀 보관 위치에 따로 둔다.
- `ETL_CUTOFF_BOUNDARY_DATE`는 `--auto`에 필요하다. 없으면 `--auto`는 종료 코드 2로 끝난다. 이 값은 §0의 경계일이 확인된 뒤에 채운다.

## 5. jar 빌드와 전달

```bash
./gradlew infoEtlJar                         # build/info-etl/info-etl.jar
ssh student@<VM> 'sudo mkdir -p /opt/info-etl && sudo chown student /opt/info-etl'
scp build/info-etl/info-etl.jar student@<VM>:/opt/info-etl/info-etl.jar
```

실행(공통 형태). 호스트에 Java가 없으므로 컨테이너로 한다.

```bash
docker run --rm --network host --env-file /etc/info-etl/etl.env \
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

COB는 23:30에 시작해 대사가 약 20분 걸린다(PH-38 측정). 새벽 시각에 부르면 cutoff가 이미 복제된 뒤다. 시각은 03:30으로 시작하고 VM 시간대를 확인한 뒤 맞춘다.

`/etc/systemd/system/info-etl.service`

```
[Unit]
Description=정보계 ETL 자동 적재
After=docker.service
Requires=docker.service

[Service]
Type=oneshot
ExecStart=/usr/bin/docker run --rm --network host --env-file /etc/info-etl/etl.env -v /opt/info-etl:/app:ro eclipse-temurin:21-jre java -Xmx2g -jar /app/info-etl.jar --auto
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

## 8. 하지 않는 것

- 레플리카와 같은 MySQL 인스턴스에 마트를 만들지 않는다.
- 비밀번호·`HMAC_KEY`를 저장소, 로그, 명령 기록, 채팅에 남기지 않는다. `etl.env`의 권한은 600이다.
- 마트 DDL 권한을 ETL 계정이나 정보계 API 계정에 주지 않는다.
- 공용 VM의 다른 컨테이너·포트를 끄거나 바꾸지 않는다.
