# P6 Python Agent Compose 실행

## 준비

`.env.corebank.example`과 `.env.fds-agent.example`을 각각 Git 저장소 밖의 `.env.corebank`,
`.env.fds-agent`로 복사한다. 두 파일에 같은 세 토큰을 주입하되 점수 호출, 조사 호출, Agent Tool·보고서
콜백 토큰은 모두 서로 다른 충분히 긴 난수여야 한다. 예제 파일에는 변수명만 두며 실제 비밀값을 커밋하거나
로그에 출력하지 않는다.

`FDS_AGENT_BUILD_CONTEXT`에는 `corebank-fds-agent` 저장소 경로를 지정하고 이미지 태그는 그 저장소의 전체
Git SHA로 고정한다.

```bash
export FDS_AGENT_BUILD_CONTEXT='../corebank-fds-agent'
export FDS_AGENT_GIT_SHA="$(git -C "$FDS_AGENT_BUILD_CONTEXT" rev-parse HEAD)"
export FDS_AGENT_TAG="$FDS_AGENT_GIT_SHA"
export COMPOSE_PROJECT_NAME='corebank-server'
export SPRING_PROFILES_ACTIVE='prod,qa-seed'
export COREBANK_ENV_FILE='/secure/path/.env.corebank'
export FDS_ENV_FILE='/secure/path/.env.fds-agent'
# latest·축약 SHA를 거부하고 두 값이 같은 전체 Git SHA인지 먼저 검증한다.
./scripts/validate-fds-agent-sha.sh
# 비밀값을 stdout에 펼치지 않고 두 Compose 파일의 병합 결과만 검증한다.
docker compose -f docker-compose.yml -f docker-compose.fds.yml config --quiet
docker compose -f docker-compose.yml -f docker-compose.fds.yml up -d --build minicore-mysql corebank-fds-agent corebank-server
```

`COMPOSE_PROJECT_NAME=corebank-server`를 고정하면 Docker Desktop에서 `corebank-fds-agent`가
`minicore-mysql` 등과 함께 `corebank-server` 프로젝트 하위에 표시된다.

`prod,qa-seed`는 로컬 QA seed 검증 예시다. O1에서는 현재 실행 중인 프로필을 먼저 확인하고 기존 값이
`prod,qa-seed`이면 그대로 유지한다. 현재 값이 `prod`라면 배포 담당자 확인 없이 `qa-seed`를 추가하지 않는다.

BE에는 정책·점수·조사 기능 스위치와 `http://corebank-fds-agent:8000` 주소가, Agent에는
`FDS_BE_INTERNAL_BASE_URL=http://corebank-server:8080`이 주입된다. 기능이 켜졌는데 필요한 토큰이 없거나
공백인 경우와 세 토큰 중 두 개가 같은 경우에는 BE가 토큰 원문을 노출하지 않고 기동에 실패한다.

## 연결과 상태 확인

```bash
docker compose -f docker-compose.yml -f docker-compose.fds.yml ps
docker exec corebank-server wget -qO- http://corebank-fds-agent:8000/health
docker port "$(docker compose -f docker-compose.yml -f docker-compose.fds.yml ps -q corebank-fds-agent)"
docker compose -f docker-compose.yml -f docker-compose.fds.yml logs --since=10m corebank-server corebank-fds-agent
```

`docker port`는 출력이 없어야 한다. Agent의 8000 포트는 `corebank-net`에서만 접근하며 호스트와 외부
프록시에 공개하지 않는다. 로그에서는 토큰 원문, 계좌번호, 고객 식별자가 출력되지 않는지 확인한다.

테스트 이체를 보류시킨 뒤 DB에서 조사 상태와 보고서를 확인한다. 정상 흐름은
`PENDING → RUNNING → COMPLETED`이고 완료 시 보고서가 한 건 존재해야 한다.

```sql
SELECT investigation_id, transfer_hold_id, job_status, attempt_count,
       next_attempt_at, lease_expires_at, last_failure_reason
FROM fds_investigation_job
ORDER BY created_at DESC
LIMIT 20;

SELECT investigation_id, schema_version, created_at
FROM fds_investigation_report
ORDER BY created_at DESC
LIMIT 20;
```

## 장애와 복구 확인

BE는 Agent healthcheck에 `depends_on: condition: service_healthy`로 종속되지 않는다. Agent가 없거나 재기동
중이어도 BE는 독립적으로 기동하고 이체 보류는 `HELD`로 유지한다. 조사 호출은 최대 3회 시도하며, 그 전의
실패는 `PENDING`으로 돌아간다. 임대 중 Agent가 중단된 경우에도 3회 미만이면 임대 만료 뒤 복구 스케줄러가
다시 선점한다.

3회 실패하거나 세 번째 임대가 만료되면 작업은 `FAILED`가 된다. `findDue`는 `PENDING`만 조회하므로 Agent
재시작만으로 `FAILED` 작업이 재개되지는 않는다. 담당자는 원인을 수정한 뒤 장애 이슈에 조사 ID와 실패 사유를
기록하고, 승인된 운영 변경으로 재처리하거나 후속 이슈로 재처리 API를 제공해야 한다. DB 상태를 임의로
`PENDING`으로 덮어쓰지 않는다.

```bash
docker compose -f docker-compose.yml -f docker-compose.fds.yml stop corebank-fds-agent
docker compose -f docker-compose.yml -f docker-compose.fds.yml ps
docker compose -f docker-compose.yml -f docker-compose.fds.yml start corebank-fds-agent
docker compose -f docker-compose.yml -f docker-compose.fds.yml logs --since=10m corebank-server corebank-fds-agent
```

중단 중 BE가 계속 응답하고 해당 이체가 `HELD`인지 확인한다. Agent 재기동 뒤 위 SQL로 남아 있던
`PENDING`이 `RUNNING → COMPLETED`로 처리되는지 확인한다. 점수·조사·Agent 토큰을 서로 바꿔 호출했을 때
각 내부 API가 거부하는지도 통합 환경에서 확인한다.

관리자 조치가 `PENDING`에 남은 경우 `FdsAdminActionRecoveryScheduler`가 P4 정본 상태를 주기적으로 다시
대조한다. 수동으로 행 상태를 덮어쓰지 말고 BE 로그와 `fds_admin_action`의 상태를 함께 확인한다.

## 종료

```bash
docker compose -f docker-compose.yml -f docker-compose.fds.yml stop corebank-server corebank-fds-agent
docker compose -f docker-compose.yml -f docker-compose.fds.yml rm -f corebank-server corebank-fds-agent
```

공용 MySQL과 Redis를 함께 제거하지 않는다.
