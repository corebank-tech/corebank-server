# P6 Python Agent 로컬 Compose 실행

## 준비

`.env.corebank.example`과 `.env.fds-agent.example`을 각각 `.env.corebank`, `.env.fds-agent`로 복사한다. 세 토큰은 서로 다른 로컬 합성값으로 채우며 실제 비밀값은 Git에 저장하지 않는다.

`FDS_AGENT_BUILD_CONTEXT`에는 `corebank-fds-agent` 저장소 경로를 지정한다.

```bash
export FDS_AGENT_BUILD_CONTEXT='../corebank-fds-agent'
export FDS_AGENT_GIT_SHA="$(git -C "$FDS_AGENT_BUILD_CONTEXT" rev-parse HEAD)"
export SPRING_PROFILES_ACTIVE='prod,qa-seed'
docker compose config
docker compose up -d --build minicore-mysql corebank-fds-agent corebank-server
```

`prod,qa-seed`는 로컬 QA seed 검증 예시다. O1에서는 현재 실행 중인 프로필을 먼저 확인하고 기존 값이 `prod,qa-seed`이면 그대로 유지한다. 현재 값이 `prod`라면 배포 담당자 확인 없이 `qa-seed`를 추가하지 않는다.

## 확인

```bash
docker compose ps
docker exec corebank-server wget -qO- http://corebank-fds-agent:8000/health
docker port "$(docker compose ps -q corebank-fds-agent)"
```

마지막 명령은 출력이 없어야 한다. Agent의 8000 포트는 `corebank-net`에서만 접근하며 호스트와 외부 프록시에 공개하지 않는다.

## 종료

```bash
docker compose stop corebank-server corebank-fds-agent
docker compose rm -f corebank-server corebank-fds-agent
```

공용 MySQL과 Redis를 함께 제거하지 않는다.
