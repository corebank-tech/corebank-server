# Swagger UI 접속 가이드

Corebank는 springdoc-openapi로 Swagger UI를 제공한다. 이 문서는 로컬/배포 환경에서 Swagger UI에 접속하는 방법과, 이를 위해 `SecurityConfig`/`SwaggerConfig`에 반영된 예외 처리를 설명한다.

## 1. 접속 URL

애플리케이션 `context-path`는 `/api/v1`이다 (`application.yml`).

| 환경 | Swagger UI | API Docs (OpenAPI JSON) |
| --- | --- | --- |
| 로컬 | `http://localhost:8080/api/v1/swagger-ui/index.html` | `http://localhost:8080/api/v1/v3/api-docs` |
| 배포 서버 | `https://api.corebank.cloud/api/v1/swagger-ui/index.html` | `https://api.corebank.cloud/api/v1/v3/api-docs` |

Swagger UI 화면 우측 상단의 **Servers** 드롭다운에서 로컬/배포 서버를 전환할 수 있다. 이 목록은 `SwaggerConfig.openAPI()`(`src/main/java/com/shinhan/corebank/common/config/SwaggerConfig.java`)의 `servers()` 설정을 따른다.

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
- 로그인 컨트롤러 도입 후에는 별도 탭에서 로그인해 세션 쿠키를 발급받은 뒤 Swagger UI로 돌아와 호출하면 된다. CSRF 보호가 활성화되어 있으므로 상태 변경 요청(POST/PUT/DELETE)에는 CSRF 토큰 헤더가 필요하다는 점에 유의한다.

## 4. 관련 문서

- **[🔗 공통 API 규칙](api_conventions.md)** §7: 엔드포인트별 인증 필요 여부
