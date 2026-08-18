# 🧰 Corebank Redis 로컬/배포 세팅 가이드

> **대상**: Corebank 프로젝트 개발 팀원 전체
> **목적**: 약관 열람 이력(TTL 30분) 등 Redis를 사용하는 기능 개발 시 로컬 환경 셋업 방법과, EC2 배포 파이프라인에서 Redis가 어떻게 뜨는지 안내
> **관련 이슈**: #67 (약관 열람 이력 Redis 저장 기능), #134 (Redis 인프라 세팅)

---

## 1. 💻 로컬 개발 셋업

프로젝트 루트에서 아래 명령으로 로컬용 Redis 컨테이너를 기동합니다.

```bash
docker compose up -d minicore-redis
```

- **접속 정보**
  - **Host**: `localhost` / **Port**: `6379`
  - 인증(Password) 없음 — 로컬 전용이며 `127.0.0.1`에만 바인딩되어 외부에서 접근 불가
- **버전**: `redis:7.4` — EC2 배포 시 사용하는 이미지와 동일 버전입니다. 버전을 올릴 때는 `docker-compose.yml`과 `.github/workflows/corebank.yml` 양쪽을 함께 수정해주세요.

앱을 `local` 프로필로 기동하면 위 컨테이너에 자동으로 연결됩니다.

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

### 왜 `local`은 `host: localhost`인가

`./gradlew bootRun`은 앱을 **호스트 머신에서 직접** 실행합니다. Docker는 `minicore-redis` 컨테이너 하나만 떠 있고, 그 포트가 `127.0.0.1:6379`로 퍼블리시되어 있으므로 호스트 관점의 `localhost:6379`가 정확히 이 컨테이너를 가리킵니다.

### 로컬 데이터 초기화

TTL 만료를 기다리지 않고 즉시 비우고 싶을 때:

```bash
docker exec minicore-redis redis-cli FLUSHALL
```

컨테이너 자체를 재생성하려면(별도 volume을 쓰지 않아 데이터도 함께 초기화됩니다):

```bash
docker compose rm -sf minicore-redis
docker compose up -d minicore-redis
```

---

## 2. 🚀 배포(EC2) 세팅

로컬과 달리 EC2 쪽 Redis는 **팀원이 직접 세팅할 필요 없이 CI/CD 파이프라인이 자동으로 띄웁니다.**

- `.github/workflows/corebank.yml`의 `deploy` job이 `main` 브랜치 push 시 SSM으로 EC2에 접속해 `docker-compose.yml`을 인라인으로 생성·실행합니다.
- 이 인라인 compose에 `corebank-redis` 서비스(image `redis:7.4`)가 `corebank-server`와 같은 `corebank-net` 브릿지 네트워크에 정의되어 있으며, **호스트에 포트를 노출하지 않습니다.** 단, 포트 미노출이 인증을 대체하진 않습니다 — `corebank-net`에 신뢰할 수 없는 컨테이너가 추가되면 그 컨테이너에서도 Redis에 접근할 수 있으므로, 이 네트워크에는 항상 우리가 정의한 컨테이너만 붙는다는 전제로 무인증 운영 중입니다. 전제가 깨지면(예: 다른 서비스 컨테이너를 같은 네트워크에 붙이는 경우) Redis ACL/password를 추가하고 Spring Secret으로 주입해야 합니다.
- `corebank-server` 컨테이너에는 `REDIS_HOST=corebank-redis` 환경변수가 주입되고, `application-prod.yml`의 `spring.data.redis.host: ${REDIS_HOST}`가 이를 읽어 연결합니다.

즉, EC2 Redis 버전을 바꾸거나 설정을 바꿔야 할 때만 `.github/workflows/corebank.yml`의 `corebank-redis` 서비스 블록을 수정하면 되고, 별도로 EC2에 수동 접속해 Redis를 설치/기동할 필요는 없습니다.

### 왜 `prod`는 `localhost`가 아니라 `${REDIS_HOST}`(컨테이너명)인가

prod에서는 앱 자체(`corebank-server`)도 Docker 컨테이너로 뜹니다. 컨테이너 안에서의 `localhost`는 그 컨테이너 자신만 가리키므로, `corebank-server` 컨테이너 안에서 `localhost:6379`를 찾으면 연결이 거부됩니다. Docker 브릿지 네트워크에 묶인 컨테이너끼리는 컨테이너/서비스 이름이 곧 DNS 호스트명이 되기 때문에, `corebank-redis`라는 이름으로 접속해야 합니다. (바로 위 `spring.datasource.url`이 `${DB_HOST}`를 쓰는 것과 같은 이유입니다.)

---

## 3. ⚙️ 설정 위치 요약

| 파일 | 설정 | 비고 |
|---|---|---|
| `application.yml` (공통) | `spring.data.redis.port: 6379` | local/prod 모두 동일한 포트라 공통으로 관리 |
| `application-local.yml` | `spring.data.redis.host: localhost` | 앱이 호스트에서 직접 실행됨 |
| `application-prod.yml` | `spring.data.redis.host: ${REDIS_HOST}` | 앱도 컨테이너라 컨테이너명으로 접속 |
| `application-test.yml` | (없음) | 현재(`#67` 머지 전)는 `TestcontainersConfig`에 Redis 컨테이너가 없어 `MySQLContainer`만 `@ServiceConnection`으로 등록됨. 테스트도 공통 `port: 6379` + 기본 host(`localhost`)를 그대로 사용. `#67`이 머지되면 Redis용 `@ServiceConnection`이 추가되어 매 테스트마다 랜덤 포트가 자동 주입되고, 이때는 yml 설정보다 우선 적용됨(Spring Boot service connection이 property보다 우선) |
| `application-scratch.yml` | (없음) | host 미지정 시 Spring Boot 기본값(`localhost`)을 타서 공통 `port: 6379`와 합쳐지면 local과 동일하게 연결됨 |

---

## 4. 🚨 트러블슈팅 & FAQ

**Q1. 로컬에서 앱 기동 시 `RedisConnectionFailureException`이 발생합니다.**
- Redis 컨테이너가 안 떠 있는 경우입니다. `docker compose up -d minicore-redis`로 먼저 기동한 뒤 `docker ps`로 `minicore-redis`가 `Up` 상태인지 확인하세요.

**Q2. 로컬에서 저장한 값이 30분도 안 됐는데 사라졌습니다.**
- `docker compose down` / `docker compose rm`으로 컨테이너를 지우면 데이터도 함께 사라집니다(별도 volume 미사용). 컨테이너를 내리지 않고 유지했는데도 사라졌다면 TTL 설정값을 코드에서 다시 확인해주세요.

**Q3. EC2에서 Redis 연결이 안 될 때는 어디를 봐야 하나요?**
- `corebank-server`와 `corebank-redis`가 같은 `corebank-net` 네트워크에 있는지, GitHub Actions 배포 로그에서 `corebank-redis` 컨테이너가 정상적으로 `docker compose up -d`에 포함되어 기동됐는지 확인하세요. Redis는 포트를 게시(publish)하지 않으므로 EC2 호스트에서 `redis-cli -h localhost`로는 접근할 수 없습니다(호스트-컨테이너 포트 매핑이 없기 때문). 연결 확인은 컨테이너 안에서 직접 `PING`을 날리는 방식을 쓰세요.

```bash
docker exec corebank-redis redis-cli PING
```
