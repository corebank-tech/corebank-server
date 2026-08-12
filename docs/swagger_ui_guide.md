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

`/swagger-ui/**`, `/v3/api-docs/**`만 `permitAll`이고, 개별 API 엔드포인트(`/products/**` GET, `/actuator/health`, `/auth/login` 제외)는 여전히 `anyRequest().authenticated()`에 걸린다. Swagger UI에서 "Try it out"으로 인증이 필요한 API를 호출하려면 브라우저에 유효한 세션 쿠키가 있어야 한다.

- 현재 `auth` 모듈에는 세션에 `AuthenticatedCustomer`를 심는 `POST /auth/login` 컨트롤러가 아직 없다. 로그인 컨트롤러가 준비되기 전까지는 인증이 필요한 API를 Swagger UI에서 직접 호출할 수 없고, `/products/**`(GET)·`/actuator/health`처럼 `permitAll`인 엔드포인트만 테스트 가능하다.
- **로그인 컨트롤러가 생긴 뒤에도 상태 변경 요청(POST/PUT/DELETE)은 바로 Swagger UI에서 테스트할 수 없다.** `SecurityConfig`는 CSRF 토큰 저장소를 별도로 지정하지 않아 Spring Security 기본값(`HttpSessionCsrfTokenRepository`)을 쓰는데, 이 저장소는 토큰을 서버 세션에만 보관하고 응답으로 클라이언트에 내려주지 않는다. 즉 지금 구조에서는 브라우저(Swagger UI 포함)가 CSRF 토큰 값을 알아낼 방법이 없어, 로그인에 성공해도 상태 변경 요청은 403(`CMN0102`)으로 막힌다.
- 이는 Swagger UI만의 문제가 아니라 CSRF 토큰을 클라이언트에 노출하는 수단 자체가 앱 전역에 없다는 문제다. 해결 방향은 `CookieCsrfTokenRepository`로 전환해 토큰을 쿠키로 내려주고, `springdoc.swagger-ui.csrf.enabled=true`(및 `cookie-name`/`header-name`)를 설정해 Swagger UI가 쿠키 값을 읽어 요청 헤더에 자동으로 실어 보내게 하는 것이다. 다만 로그인 컨트롤러가 없는 지금은 "로그인 → 쿠키 발급 → Swagger에서 상태 변경 호출"까지 이어지는 흐름을 끝까지 검증할 수 없으므로, 이 작업은 로그인 컨트롤러 도입과 묶어 별도 이슈로 진행한다.

## 4. 관련 문서

- **[🔗 공통 API 규칙](api_conventions.md)** §7: 엔드포인트별 인증 필요 여부
