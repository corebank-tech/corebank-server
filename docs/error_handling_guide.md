# 오류코드 공통 인프라 — 사용 가이드

**관리**: P5 (공통기반)
**연계 문서**: [api_conventions.md](api_conventions.md) — 오류코드 마스터·Enum·응답 규약의 원본

> **v1.1 변경사항**
> - `CommonErrorCode` 추가 — 핸들러에 문자열로 박혀 있던 `"CMN0001"` · `"CMN9999"`를 Enum으로 이전
> - `GlobalExceptionHandler` → `ApiExceptionHandler` 개명 + REST 컨트롤러로 범위 한정
> - `ErrorResponse`에서 `status` · `error` 필드 제거 — HTTP 헤더에 이미 있는 정보라 body에서 중복 제거. 성공 응답과 `{code, message, data}` 구조를 정확히 일치시킴

> ⚠️ 이 문서는 팀 컨벤션 참고용이며, 아래 5개 파일은 아직 이 저장소에 구현되어 있지 않습니다. 실제 코드로 반영하려면 별도로 작업을 요청해 주세요.

---

## 제공 파일 5개

```
common/exception/ErrorCode.java                    ← 각 파트가 구현할 인터페이스
common/exception/BusinessException.java            ← 각 파트가 던질 예외
common/exception/CommonErrorCode.java              ← 공통 오류코드. 그대로 사용
adapter/in/web/exception/ErrorResponse.java        ← 오류 응답 DTO. 직접 안 만듦
adapter/in/web/exception/ApiExceptionHandler.java  ← 자동 동작. 손댈 필요 없음
```

## 1. 파일 배치 (헥사고날 기준)

```
com.example
├─ common/
│  └─ exception/                     ← 팀 전체 공용. 그대로 복사
│     ├─ ErrorCode.java
│     ├─ BusinessException.java
│     └─ CommonErrorCode.java
│
├─ adapter/
│  └─ in/web/exception/              ← 팀 전체 공용. 그대로 복사
│     ├─ ErrorResponse.java
│     └─ ApiExceptionHandler.java
│
└─ product/                          ← 각자 도메인 (product, account, transfer...)
   ├─ domain/
   │  └─ ProductService.java         ← 여기서 BusinessException 던짐
   │  └─ exception/
   │     └─ ProductErrorCode.java    ← 각자 여기에 자기 Enum 만듦
   └─ application/
```

**왜 `common`이 `application` 밑이 아닌가**

`ErrorCode` · `BusinessException`은 각 파트의 도메인 계층에서 던지는 물건입니다. 헥사고날 의존 방향은 `adapter → application → domain`이라 domain이 application을 참조하면 의존성이 역전됩니다. 어느 계층에서도 부담 없이 import할 수 있도록 중립 위치(`common`)에 둡니다.

공통 5개는 한 번만 만들고, 이후 각자 할 일은 자기 도메인 폴더에 Enum 하나 추가하는 것뿐입니다.

---

## 2. 공통 파일 — 그대로 복사

### `common/exception/ErrorCode.java`

```java
package com.example.common.exception;

public interface ErrorCode {

    String getCode();

    int getStatus(); // HttpStatus에 의존하지 않는 순수 int

    String getMessage();
}
```

반환 타입이 `HttpStatus`가 아니라 `int`인 이유: `HttpStatus`를 반환하면 도메인 계층이 Spring Web에 의존하게 됩니다. HTTP 변환은 어댑터인 `ErrorResponse`에서만 일어납니다.

### `common/exception/BusinessException.java`

```java
package com.example.common.exception;

public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
```

### `common/exception/CommonErrorCode.java`

```java
package com.example.common.exception;

public enum CommonErrorCode implements ErrorCode {

    INVALID_INPUT("CMN0001", 400, "입력값이 올바르지 않습니다."),
    REQUIRED_FIELD_MISSING("CMN0002", 400, "필수 입력값이 누락되었습니다."),
    INVALID_DATE_RANGE("CMN0003", 400, "조회 시작일이 종료일보다 늦습니다."),
    DATE_RANGE_EXCEEDED("CMN0004", 400, "조회기간이 최대 1년을 초과했습니다."),
    INVALID_PAGE_SIZE("CMN0005", 400, "지원하지 않는 페이지 크기입니다."),
    ALL_QUERY_TOO_LARGE("CMN0006", 400, "전체조회 결과가 너무 많습니다. 조회기간을 좁혀 주세요."),

    UNAUTHORIZED("CMN0101", 401, "인증정보가 없거나 세션이 만료되었습니다."),
    FORBIDDEN("CMN0102", 403, "해당 자원에 접근할 권한이 없습니다."),

    INVALID_ENDPOINT("CMN0201", 404, "잘못된 요청 주소입니다."),

    DUPLICATE_REQUEST_IN_PROGRESS("CMN0301", 409, "동일 요청이 처리 중입니다."),
    IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST("CMN0302", 409, "동일한 멱등키가 다른 요청에 사용되었습니다."),

    INTERNAL_ERROR("CMN9999", 500, "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");

    private final String code;
    private final int status;
    private final String message;

    CommonErrorCode(String code, int status, String message) {
        this.code = code;
        this.status = status;
        this.message = message;
    }

    @Override public String getCode()    { return code; }
    @Override public int    getStatus()  { return status; }
    @Override public String getMessage() { return message; }
}
```

### `adapter/in/web/exception/ErrorResponse.java`

```java
package com.example.adapter.in.web.exception;

import com.example.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

// 실패 응답 전용 봉투. 성공 응답은 ApiResponse 가 전담한다
public record ErrorResponse(String code, String message, Object data) {

    public static ResponseEntity<ErrorResponse> toResponseEntity(ErrorCode errorCode) {
        return toResponseEntity(errorCode, errorCode.getMessage());
    }

    public static ResponseEntity<ErrorResponse> toResponseEntity(ErrorCode errorCode, String message) {
        return ResponseEntity
                .status(HttpStatus.valueOf(errorCode.getStatus()))
                .body(new ErrorResponse(errorCode.getCode(), message, null));
    }
}
```

### `adapter/in/web/exception/ApiExceptionHandler.java`

```java
package com.example.adapter.in.web.exception;

import com.example.common.exception.BusinessException;
import com.example.common.exception.CommonErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

// 모든 예외를 가로채 {code, message, data} 로 변환. 각 파트는 수정할 필요 없음
// annotations = RestController.class 로 스코프를 제한하지 않는다: 404/405 처럼 핸들러 매칭 자체가
// 실패한 예외는 handlerType 을 알 수 없어 스코프가 걸린 advice 후보에서 아예 제외되기 때문
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    // 각 파트가 직접 던진 예외 -> 그 Enum 의 코드·상태·메시지 그대로 반환
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        log.warn("[{}] {}", e.getErrorCode().getCode(), e.getMessage());
        return ErrorResponse.toResponseEntity(e.getErrorCode(), e.getMessage());
    }

    // @Valid 본문 검증 실패 (예: @Min(1) 인데 0) -> CMN0001
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        FieldError first = e.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        String message = (first == null)
                ? CommonErrorCode.INVALID_INPUT.getMessage()
                : "%s: %s".formatted(first.getField(), first.getDefaultMessage());
        log.warn("[{}] {}", CommonErrorCode.INVALID_INPUT.getCode(), message);
        return ErrorResponse.toResponseEntity(CommonErrorCode.INVALID_INPUT, message);
    }

    // 파라미터 타입 불일치 (예: Long 자리에 abc, Enum 에 없는 값) -> CMN0001
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        String message = "'%s' 파라미터 값이 올바르지 않습니다.".formatted(e.getName());
        log.warn("[{}] {}", CommonErrorCode.INVALID_INPUT.getCode(), message);
        return ErrorResponse.toResponseEntity(CommonErrorCode.INVALID_INPUT, message);
    }

    // 필수 쿼리 파라미터 누락 -> CMN0002
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException e) {
        String message = "필수 파라미터 '%s' 가 누락되었습니다.".formatted(e.getParameterName());
        log.warn("[{}] {}", CommonErrorCode.REQUIRED_FIELD_MISSING.getCode(), message);
        return ErrorResponse.toResponseEntity(CommonErrorCode.REQUIRED_FIELD_MISSING, message);
    }

    // 필수 헤더 누락 (예: Idempotency-Key 미전송) -> CMN0002
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException e) {
        String message = "필수 헤더 '%s' 가 누락되었습니다.".formatted(e.getHeaderName());
        log.warn("[{}] {}", CommonErrorCode.REQUIRED_FIELD_MISSING.getCode(), message);
        return ErrorResponse.toResponseEntity(CommonErrorCode.REQUIRED_FIELD_MISSING, message);
    }

    // 존재하지 않는 경로 호출 -> CMN0201 (404)
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ErrorResponse> handleNotFound(Exception e) {
        log.warn("[{}] {}", CommonErrorCode.INVALID_ENDPOINT.getCode(), e.getMessage());
        return ErrorResponse.toResponseEntity(CommonErrorCode.INVALID_ENDPOINT);
    }

    // 경로는 존재하나 지원하지 않는 HTTP 메서드로 호출 -> CMN0201 (404). 405 대신 404 로 통일
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("[{}] {}", CommonErrorCode.INVALID_ENDPOINT.getCode(), e.getMessage());
        return ErrorResponse.toResponseEntity(CommonErrorCode.INVALID_ENDPOINT);
    }

    // 그 외 모든 예외 (NPE, DB 오류 등) -> CMN9999. 스택트레이스는 로그에만 남김
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("처리되지 않은 예외 발생", e);
        return ErrorResponse.toResponseEntity(CommonErrorCode.INTERNAL_ERROR);
    }
}
```

핸들러 8개가 처리하는 상황:

| 핸들러 | 처리 상황 | 반환 코드 |
| --- | --- | --- |
| `BusinessException` | 각 파트가 명시적으로 던진 예외 | 해당 Enum의 코드 |
| `MethodArgumentNotValidException` | `@Valid` 바디 검증 실패 | `CMN0001` |
| `MethodArgumentTypeMismatchException` | 타입 불일치. Enum에 없는 값 전달 | `CMN0001` |
| `MissingServletRequestParameterException` | 필수 쿼리 파라미터 누락 | `CMN0002` |
| `MissingRequestHeaderException` | 필수 헤더 누락 (`Idempotency-Key` 등) | `CMN0002` |
| `NoResourceFoundException` / `NoHandlerFoundException` | 존재하지 않는 엔드포인트 (404) | `CMN0201` |
| `HttpRequestMethodNotSupportedException` | 지원하지 않는 HTTP 메서드 (405 → 404로 통일) | `CMN0201` |
| `Exception` | 그 외 모든 예외 | `CMN9999` |

---

## 3. 각자 할 일 ① — 자기 도메인 오류코드 Enum 만들기

`ErrorCode`를 구현한 Enum을 자기 모듈 `domain/exception/`에 만듭니다.

```java
package com.example.product.domain.exception;

import com.example.common.exception.ErrorCode;

public enum ProductErrorCode implements ErrorCode {

    INVALID_SUBSCRIPTION_AMOUNT("PRD0001", 400, "가입금액이 상품 한도 범위를 벗어났습니다."),
    NOT_FOUND("PRD0201", 404, "상품을 찾을 수 없습니다."),
    NOT_ON_SALE("PRD0301", 409, "판매중인 상품이 아닙니다.");

    private final String code;
    private final int status;
    private final String message;

    ProductErrorCode(String code, int status, String message) {
        this.code = code;
        this.status = status;
        this.message = message;
    }

    @Override public String getCode()    { return code; }
    @Override public int    getStatus()  { return status; }
    @Override public String getMessage() { return message; }
}
```

> 위 `ProductErrorCode`는 형태를 보여주는 예시입니다. 실제 값은 [api_conventions.md](api_conventions.md) §4의 자기 도메인 표를 그대로 옮기면 됩니다.

### 접두어 배정 · 번호 구간

전체 배정표와 번호 구간 규칙은 [api_conventions.md §3](api_conventions.md#3-오류코드-체계-req-cmn-008)을 따릅니다. 배정표에 없는 접두어는 사용 금지 — 새 접두어가 필요하면 그 문서에 먼저 등록합니다.

### 새로 만들지 말고 `CommonErrorCode`를 쓸 상황

| 상황 | 사용할 코드 |
| --- | --- |
| 입력값 검증 실패 (400) | `INVALID_INPUT` — `@Valid`가 자동 처리 |
| 필수값 누락 (400) | `REQUIRED_FIELD_MISSING` |
| 조회 시작일 > 종료일 | `INVALID_DATE_RANGE` |
| 조회기간 1년 초과 | `DATE_RANGE_EXCEEDED` |
| 지원하지 않는 페이지 크기 | `INVALID_PAGE_SIZE` |
| `all=true` 전체조회 결과가 도메인별 상한 초과 | `ALL_QUERY_TOO_LARGE` |
| 세션 없음·만료 (401) | `UNAUTHORIZED` |
| 타 고객 자원 접근 (403) | `FORBIDDEN` |
| 존재하지 않는 엔드포인트·지원하지 않는 메서드 (404) | 던지지 않아도 자동으로 `INVALID_ENDPOINT` |
| 멱등키 중복 처리중 (409) | `DUPLICATE_REQUEST_IN_PROGRESS` |
| 멱등키 재사용+요청 내용 다름 (409) | `IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST` |
| 예상 못 한 서버 오류 (500) | 던지지 않아도 자동으로 `INTERNAL_ERROR` |

401 인증 만료에 파트마다 코드를 만들면 프론트가 분기할 수 없습니다. 실제로 통합 전 명세서에서 같은 상황에 `ATH0001` · `ATH0002` · `AUTH0001` 세 코드가 쓰이고 있었습니다.

---

## 4. 각자 할 일 ② — 도메인 로직에서 던지기

```java
package com.example.product.domain;

import com.example.common.exception.BusinessException;
import com.example.product.domain.exception.ProductErrorCode;

public class ProductService {

    public Product getProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.NOT_FOUND));

        if (!product.isOnSale()) {
            throw new BusinessException(ProductErrorCode.NOT_ON_SALE);
        }
        return product;
    }
}
```

**공통 코드를 던질 때**

```java
if (fromDate.isAfter(toDate)) {
    throw new BusinessException(CommonErrorCode.INVALID_DATE_RANGE);
}
```

**런타임 값을 메시지에 넣어야 할 때**

```java
throw new BusinessException(
        AuthErrorCode.LOGIN_FAILED,
        "아이디 또는 비밀번호가 일치하지 않습니다. (남은 시도 횟수: %d회)".formatted(remainingAttempts)
);
```

**이게 전부입니다.** `try-catch`로 응답을 조립하지 않습니다. 던지기만 하면 `ApiExceptionHandler`가 잡아 `{code, message, data}`로 변환합니다.

---

## 5. 컨트롤러 — 실패 처리는 신경 쓸 게 없음

```java
@GetMapping("/api/v1/products/{productId}")
public ApiResponse<ProductResponse> getProduct(@PathVariable Long productId) {
    return ApiResponse.success(productService.getProduct(productId).toResponse());
    // 예외가 나면 이 메서드는 끝까지 실행되지 않고
    // ApiExceptionHandler 가 대신 응답을 만든다
}
```

성공/실패 분기를 컨트롤러에 짤 필요가 없습니다. 정상 흐름만 작성하면 됩니다.

---

## 6. 실제로 나가는 응답

**성공** — HTTP 200

```json
{
  "code": "0000",
  "message": "정상 처리되었습니다.",
  "data": { "productId": 2001, "productName": "정기예금 A" }
}
```

**도메인 오류** (`ProductErrorCode.NOT_FOUND`) — HTTP 404

```json
{
  "code": "PRD0201",
  "message": "상품을 찾을 수 없습니다.",
  "data": null
}
```

**입력값 검증 실패** (`@Valid` 자동 처리, 아무것도 안 던져도 됨) — HTTP 400

```json
{
  "code": "CMN0001",
  "message": "amount: 1 이상이어야 합니다",
  "data": null
}
```

**예상 못 한 서버 오류** — HTTP 500, 스택트레이스는 서버 로그에만

```json
{
  "code": "CMN9999",
  "message": "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.",
  "data": null
}
```

성공·실패가 모두 `{code, message, data}` 3개 키로 동일합니다. 프론트는 `code`가 `"0000"`인지만 보면 됩니다.

---

## 7. 예외처리 흐름

| 단계 | 일어나는 일 |
| --- | --- |
| 1 | 컨트롤러는 정상 흐름만 작성. try-catch 없음 |
| 2 | 도메인 로직이 검증 실패를 감지 |
| 3 | `throw new BusinessException(내Enum.VALUE)` |
| 4 | unchecked 예외라 try-catch 없이 위로 전파 |
| 5 | `ApiExceptionHandler`가 `@RestControllerAdvice`로 자동 포착. `ErrorCode`의 3개 메서드만 호출 |
| 6 | `ErrorResponse`가 int 상태코드를 `HttpStatus`로 변환해 JSON 응답 |

핸들러가 `BusinessException` 하나만 안다는 게 핵심입니다. P3이 `PRD0301`을 새로 만들어도 P5의 핸들러 코드는 그대로입니다. 만약 파트마다 다른 예외 클래스를 던지면 파트가 오류를 추가할 때마다 핸들러에 `@ExceptionHandler`를 추가해야 하고, 결국 P5 파일을 계속 고쳐야 합니다.

---

## 8. 체크리스트

- [ ] 공통 5개 파일을 그대로 복사했는가 (수정하지 않았는가)
- [ ] 자기 도메인 오류코드 Enum을 `domain/exception/`에 만들었는가
- [ ] 접두어가 배정표에 있는 것인가
- [ ] 401·403·조회기간·페이지크기 오류를 새로 안 만들고 `CommonErrorCode`를 썼는가
- [ ] `throw new BusinessException(...)` 로만 던졌는가 (컨트롤러에서 try-catch 안 함)
- [ ] 컨트롤러가 성공 응답만 반환하는가
- [ ] 새로 만든 오류코드를 [api_conventions.md](api_conventions.md) §4 표에 추가했는가
