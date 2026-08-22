# Swagger UI 접속 가이드

Corebank는 springdoc-openapi로 Swagger UI를 제공한다. 이 문서는 로컬/배포 환경에서 Swagger UI에 접속하는 방법과, 이를 위해 `SecurityConfig`/`SwaggerConfig`에 반영된 예외 처리를 설명한다.

## 1. 접속 URL

애플리케이션 `context-path`는 `/api/v1`이다 (`application.yml`).

| 환경 | Swagger UI | API Docs (OpenAPI JSON) |
| --- | --- | --- |
| 로컬 | `http://localhost:8080/api/v1/swagger-ui/index.html` | `http://localhost:8080/api/v1/v3/api-docs` |
| 배포 서버 | `https://api.corebank.cloud/api/v1/swagger-ui/index.html` | `https://api.corebank.cloud/api/v1/v3/api-docs` |

Swagger UI 화면 우측 상단의 **Servers** 드롭다운은 "Try it out" 요청을 보낼 base URL만 바꿔준다. 이 목록은 `SwaggerConfig.openAPI()`(`src/main/java/com/shinhan/corebank/common/config/SwaggerConfig.java`)의 `servers()` 설정을 따른다.

드롭다운으로 origin을 바꾸는 것만으로 로컬↔배포 서버를 자유롭게 오가며 테스트할 수 있는 것은 아니다. 세션 쿠키는 발급받은 origin에서만 유효해서, 로컬 Swagger UI에서 배포 서버로 전환해 호출하면 별도 CORS/credentials 설정 없이는 세션이 전달되지 않고, 반대로 배포(HTTPS) 페이지에서 로컬(HTTP) 서버를 호출하면 브라우저의 mixed content 정책에 막힌다. 그래서 실제 API 호출 테스트는 항상 **접속 중인 Swagger UI와 같은 origin의 서버**로만 진행한다 — 로컬 테스트는 `http://localhost:8080/...` Swagger UI에서, 배포 서버 테스트는 `https://api.corebank.cloud/...` Swagger UI에서. 드롭다운은 명세서에 등록된 서버 URL을 확인하는 용도로 참고한다.

## 2. 인증 없이 접속되는 이유

Spring Security 기본 설정(`anyRequest().authenticated()`)을 그대로 두면 Swagger UI 정적 리소스와 API Docs 요청도 세션 인증을 요구해 401로 막힌다. 이를 막기 위해 `SecurityConfig.securityFilterChain()`(`src/main/java/com/shinhan/corebank/auth/adapter/in/security/SecurityConfig.java`)에 아래 예외를 추가했다.

```java
// Swagger-UI/API 문서는 인증 없이 접근 가능하도록 공개
.requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
```

즉 **Swagger UI 화면과 API 명세 조회 자체는 로그인 없이 열람 가능**하다.

## 3. 인증이 필요한 API를 Swagger UI에서 호출하기

`/swagger-ui/**`, `/v3/api-docs/**`만 `permitAll`이고, 인증 대상 API는 유효한 로그인 세션이 필요하다. Swagger UI에서 아래 순서로 같은 origin의 API를 호출한다.

1. `POST /auth/login`을 실행해 로그인한다. 성공하면 브라우저에 `JSESSIONID`와 `XSRF-TOKEN` 쿠키가 발급된다.
2. 조회 API는 로그인 세션 쿠키가 자동으로 전달되므로 바로 실행한다.
3. 상태 변경 API는 `application.yml`의 `springdoc.swagger-ui.csrf.enabled=true` 설정에 따라 Swagger UI가 `XSRF-TOKEN` 쿠키 값을 `X-XSRF-TOKEN` 헤더로 전달한다.

회원가입처럼 `SecurityConfig`에서 CSRF 검사를 제외한 공개 API는 로그인 없이 실행할 수 있다. 인증 API를 테스트할 때는 세션 쿠키의 origin이 일치해야 하므로 로컬 Swagger UI에서는 로컬 API를, 배포 Swagger UI에서는 배포 API를 사용한다.

## 4. 관련 문서

- **[🔗 공통 API 규칙](api_conventions.md)** §7: 엔드포인트별 인증 필요 여부
