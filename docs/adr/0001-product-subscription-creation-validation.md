# ADR-0001. Product / ProductSubscription 생성 시점 검증 정책

- 상태: 수락됨 (2026-08-19)
- 관련: 이슈 #76, 계기 PR #65 리뷰(cy389), 영향 이슈 #69(상품가입 실행 API), #68(사전검증)

## 맥락

PR #65 리뷰에서 `ProductSubscription`이 `@AllArgsConstructor` + `@Builder`만 가지고 있어
`customerId = null`, `subscriptionAmount = -1` 같은 유효하지 않은 조합도 생성 가능하다는 지적을 받았다.

`ProductSubscription`은 `Product`와 같은 역할(JPA 엔티티를 1:1로 읽기 매핑하는 플랫 도메인 객체)을
의도적으로 같은 패턴으로 작성한 것이고, `Product` 역시 검증이 전혀 없다. `ProductSubscription`에만
검증을 추가하면 같은 역할의 두 클래스가 서로 다른 규칙을 갖게 된다.

현재 논리는 "두 클래스 다 DB에서 읽어 매핑되는 용도로만 쓰이므로 DB `NOT NULL` 제약이 이미 보장하는
값을 애플리케이션에서 다시 검증하는 것은 중복"이라는 것이다. 쟁점은 이 전제가 앞으로도 유효한가다.

### 코드 확인 결과 (2026-08-19, `dev` / `feat/68-product-subscription-validation` / `feat/69-product-subscription-execution`)

| 클래스 | 현재 생성 경로 | 향후 생성 경로 |
| --- | --- | --- |
| `Product` | `ProductMapper` 한 곳 (조회 유스케이스만 존재) | 없음 — 상품 등록 기능은 이 프로젝트 범위 밖 |
| `ProductSubscription` | `ProductSubscriptionMapper` 한 곳 | #69 상품가입 실행 API에서 서비스 로직이 직접 조립 |

즉 두 클래스의 전제가 실제로 갈라진다. `Product`는 "읽기 전용"이 계속 유지되지만
`ProductSubscription`은 그렇지 않다.

## 결정

**두 클래스를 하나의 정책으로 묶지 않고 개별 결정한다.**

### 1. `Product` — 무검증 유지

생성 경로가 매퍼뿐이고 앞으로도 생기지 않으므로, DB 제약이 보장하는 값을 재검증하지 않는다.
`@AllArgsConstructor` + `@Builder`를 그대로 둔다.

### 2. `ProductSubscription` — 검증된 정적 팩토리 `create(...)` 추가

기존 `@Builder`는 **매퍼 재구성 전용**으로 남기고, 신규 생성용으로 검증을 수행하는
`ProductSubscription.create(...)`를 별도로 추가해 공존시킨다.

- **선례**: `transfer` 도메인의 `Transfer.create()` + package-private `TransferValidations` 조합을 따른다.
  검증 실패는 `BusinessException(ErrorCode)`로 던진다.
- **`account` 도메인 방식은 따르지 않는다.** `Account` / `AccountNumberSequence`는 `IllegalStateException`을
  직접 던지는데, 이러면 `GlobalExceptionHandler`가 `CMN9999`(500)로 처리하게 되어
  클라이언트가 원인을 구분할 수 없다. 상품가입은 사용자 입력에서 출발하는 흐름이므로 400대 코드가 맞다.

#### 검증 범위

PR #65가 지적한 **자체 불변식(self-invariant)** 으로 한정한다.

- 필수 식별자 null 여부: `customerId`, `productId`, `withdrawalAccountId`
- 금액·기간의 양수 여부: `subscriptionAmount > 0`, `termMonths > 0`

**`accountId`는 검증 대상이 아니다.** 가입 결과로 개설되는 계좌(`account_id BIGINT NULL
COMMENT '가입으로 개설된 계좌'`)라 조립 시점에는 아직 없고, `PROCESSING` / `ERROR` 상태
레코드에는 끝까지 null로 남는다. 필수 계좌는 초입금 출금계좌인 `withdrawal_account_id`
(`NOT NULL`) 쪽이다.

**`Product`의 min/max 정책과의 교차 검증은 제외한다.** 가입금액이 `product.min_amount` ~ `max_amount`
범위인지(`PRD0001`), 가입기간이 `min_term_months` ~ `max_term_months` 범위인지(`PRD0002`),
`amount_unit`의 배수인지(`PRD0004`)는 **#68 사전검증의 책임**이다. 도메인 팩토리가 `Product`를
알아야 판정할 수 있는 규칙이므로 여기에 넣으면 책임이 섞인다.

#### 오류코드 — 신규 enum을 만들지 않는다

필수값 검증은 `Transfer.create()`와 동일하게 `CommonErrorCode`를 쓴다. 금액 검증은 다르다 —
`Transfer`는 `TransferErrorCode.INVALID_AMOUNT`(`TRF0003`)를 쓰지만 여기서는
`CommonErrorCode.INVALID_INPUT`을 쓴다. 아래 3번(계층) 때문이다.

| 검증 | 오류코드 |
| --- | --- |
| 필수 식별자 null | `CommonErrorCode.REQUIRED_FIELD_MISSING` (`CMN0002`, 400) |
| 금액·기간 비양수 | `CommonErrorCode.INVALID_INPUT` (`CMN0001`, 400) |

이슈 #76의 최초 결론 코멘트는 "신규 `ProductSubscriptionErrorCode`"를 만든다고 적었으나, 코드 확인 후 철회한다.

1. `subscription/application/SubscriptionErrorCode.java`가 **이미 존재한다**(`PRD0203`, `PRD9001`).
   enum을 하나 더 만들면 같은 도메인의 오류코드가 두 군데로 쪼개진다.
2. 위 검증 범위에는 **새 오류코드 자체가 필요 없다.** `CommonErrorCode`가 전부 커버하고,
   상품 고유 코드인 `PRD0001` / `PRD0002` / `PRD0004`는 위에서 범위 제외한 교차 검증 전용이다.
3. **계층 문제도 함께 해소된다.** `SubscriptionErrorCode`는 `application` 패키지에 있는데
   `create()`는 `domain`에 놓인다. 도메인이 애플리케이션 계층을 참조하면 헥사고날 의존 방향 위반이다
   (`transfer`는 `domain/exception/`에 둬서 이 문제가 없다). `common.exception`만 참조하면 위반이 없다.

향후 정말로 subscription 도메인 고유 오류코드가 필요해지면 `application`이 아니라
`subscription/domain/exception/`에 둔다.

#### 구현 시점

**이 이슈에서는 코드를 변경하지 않는다.** `create()`는 실제 호출자가 생기는 **#69 PR에서 함께 추가**한다.
호출자 없이 팩토리만 먼저 추가하면 죽은 코드이고, 실제 조립 시점에 필요한 파라미터 구성이 드러나기 전에
시그니처를 고정하게 된다.

## 검토한 대안

### (A) 계속 무검증 유지

`ProductSubscription`이 #69에서 서비스 로직으로 직접 조립되는 순간 전제가 깨진다. 매퍼만 쓰던 시절엔
DB `NOT NULL`이 사후에 잡아줬지만, 서비스 조립 경로에서는 잘못된 객체가 트랜잭션 후반의
DB 제약 위반으로 터지면서 원인 추적이 어려워진다. 기각.

### (B) 두 클래스 모두에 검증 추가 (정책 통일)

일관성은 얻지만 `Product`는 생성 경로가 실제로 존재하지 않는다. 호출되지 않는 검증을 유지·테스트하는
비용만 남는다. "같은 패턴이니 같은 규칙"이라는 형태상의 일관성보다 "생성 경로가 있는가"라는
실질 기준으로 나누는 편이 낫다고 판단. 기각.

### (C) 빌더를 없애고 검증 생성자 하나로 통일

매퍼가 DB에서 읽어 재구성하는 경로까지 검증을 타게 된다. 이미 DB에 저장된 레거시 데이터가 검증을
통과하지 못하면 **조회 자체가 실패**한다. 재구성과 신규 생성은 분리해야 한다. 기각.

## 결과

- `Product`: 변경 없음. 무검증 유지가 명시적 결정으로 기록됨.
- `ProductSubscription`: `@Builder`는 매퍼 전용으로 남고, #69에서 `create(...)` + package-private
  `ProductSubscriptionValidations` 추가. 오류코드는 `CommonErrorCode`만 사용.
- 두 클래스가 다른 규칙을 갖게 되지만, 그 근거(생성 경로 유무)가 이 문서에 남는다.
- `ProductSubscriptionMapper`는 계속 `builder()`를 쓴다 — 검증을 타지 않아야 하므로 의도적이다.
  #69 구현 시 `create()`로 잘못 바꾸지 않도록 주의.
