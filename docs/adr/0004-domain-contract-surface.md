# ADR-0004. 도메인 간 계약면은 `<domain>.api` 하나로 한다

- 상태: 제안 (2026-09-25) — 팀 합의 후 수락
- 관련: 이슈 #359, 선행 측정 #349, 후속 문서화 #350, 선례 ADR-0002 · ADR-0003

## 맥락

`#349`에서 계층 규칙을 전 도메인으로 확대하며 도메인 간 참조를 전수 측정하다가,
**모듈 API가 두 개인 상태**가 드러났다.

- `README.md` 도메인 구조 절 — "다른 도메인은 이 패키지(`api/`)를 통해서만 접근하고, 상대
  도메인의 `application`·`domain`·`adapter`를 직접 참조하지 않습니다"
- [ADR-0002](0002-cross-domain-account-read-mechanism.md) — "소유 도메인의 **공개 UseCase**를
  경유한다". 그 예시인 `WithdrawableAccountQueryUseCase`는 `account.application.port.in`에 있다

두 문서가 서로 다른 패키지를 계약면으로 지목한다. 그래서 `AGENTS.md` 규칙 2가 지금
**둘 다 정상**으로 열어 두고 "#359에서 정한다"고 유예해 둔 상태다. 이 유예가 `terms`·`batch`
외 도메인으로 캡슐화 ArchUnit 규칙을 확대하지 못하게 막고 있다.

### 실측

도메인 13개 사이의 `import`만 셌다(같은 도메인 내부 참조와 `common` 제외).

| 시점 | 총 | `api` 경유 | 계약면 우회 |
|---|---:|---:|---:|
| 2026-09-10 (#349) | 130 | 60 | 70 |
| **2026-09-25 (`dev`)** | **151** | **78** | **73** |

**새 코드는 `api`로 가는데(60 → 78) 기존 우회는 줄지 않는다(70 → 73).** 규칙이 둘이라
무엇이 위반인지 사람마다 다르게 판단하고, 그래서 아무도 옛 참조를 옮기지 않는다.

우회 73건의 성격은 둘로 갈린다.

- **`application` 37건** — 호출당하는데 `api`가 없는 도메인들이다. `transfer`(피호출 27) ·
  `product`(20) · `autotransfer`(2) · `scheduledtransfer`(2)에 계약면이 없어 호출하는 쪽이
  `application.port.in`을 직접 잡는다. **`api`가 있는데도 우회당하는 도메인도 있다** —
  `account`는 피호출 31건 중 18건이, `limit`은 7건 중 4건이 우회다.
- **`domain` 36건 — 34건이 행위 없는 enum과 에러코드다.** `account.AccountType`(9) ·
  `product.ProductGroup`(8) · `transfer.TransferChannel`(3) · `product.ProductErrorCode`(3) ·
  `limit.LmtErrorCode`(3) 등. 실제 애그리거트는 `Product`·`ProductDetail` 2건뿐이다.

공유 어휘를 어디에 둘지 규칙이 없어서 반반으로 갈렸다 — `otp`는 `otp.api.OtpTransactionType`
으로 계약에 두고, `account`·`product`·`transfer`·`limit`은 `domain`에 두어 외부가 `domain`을
직접 참조한다. **어느 쪽이 틀린 게 아니라 정한 적이 없다.**

## 결정

### 1. 계약면은 `<domain>.api` 하나다

다른 도메인이 참조할 수 있는 것은 `<domain>.api` 아래뿐이다. `application`·`domain`·`adapter`는
그 도메인의 내부다. ADR-0002의 "공개 UseCase"는 **`api`에 둔 UseCase**를 뜻하는 것으로 읽고,
문서 표현을 그렇게 정리한다(후속 작업).

### 2. 공유 어휘(enum·에러코드)도 `<domain>.api`에 둔다

여러 도메인이 함께 쓰는 enum과 `ErrorCode`는 소유 도메인의 `api`에 둔다. `otp.api.OtpTransactionType`
선례를 따른다. `common`으로 옮기지 않는다(대안 D).

이 선택은 `#349`가 `api`를 leaf 계층으로 고정하고 `Api mayNotAccessAnyLayer()`를 건 것과 정합적이다 —
**계약은 아무것도 의존하지 않는 순수 표면**이라는 전제이고, 행위 없는 enum·에러코드가 정확히 거기 맞는다.

### 3. 2차에 하는 것은 결정과 신규 코드 규칙까지다

- **새로 쓰는 크로스 도메인 호출은 반드시 `api`를 경유한다.** 리뷰에서 막는다.
- **기존 우회 73건을 2차에 일괄 이관하지 않는다.** `docs/phase2/tasks.md`가 #359에 배정한 것은
  결정 ADR 1건(0.5일)이고 이관·ArchUnit 확대 태스크는 2차 계획에 없다. 일괄 이관은 3차나
  별도 합의로 잡는다.
- **도메인이 `api`를 새로 만들 때 그 도메인으로 향하는 우회를 함께 옮긴다.** 가장 큰 덩어리인
  `transfer`(27건)는 **PH-99(10/2)가 `transfer.api`를 신설**하므로 그 자리가 기회다. 다만 PH-99의
  범위는 훅 인터페이스 3개이므로, 27건 이관을 그 PR에 끼워 넣을지는 P4가 판단한다.
- **에이전트는 기존 참조를 선제적으로 정리하지 않는다**(`AGENTS.md` 규칙 2 유지). 무관한 PR의
  변경 범위가 번지는 것을 막는다.

### 4. ArchUnit 확대는 `api`를 가진 도메인부터 점진 적용한다

`TermsArchitectureTest`·`BatchArchitectureTest` 형태(해당 도메인 외부는 `<domain>.api`만 의존)를
도메인별로 복제한다. **우회가 0인 도메인부터 건다** — 규칙을 먼저 걸고 코드를 맞추는 것이 아니라,
맞은 도메인을 잠그는 순서다. 전 도메인 일괄 적용은 우회 73건이 남아 있는 동안 불가능하다.

### 5. ADR-0002의 승인된 예외는 유지한다

`transfer`의 `AccountLockJpaEntity` 부분 매핑 1건이다. 새 예외는 ADR과 소유 도메인 합의가 있어야 한다.

## 검토한 대안

### (A) `application.port.in`으로 단일화하고 `api`를 없앤다

ADR-0002의 문구를 그대로 따르는 안이다. 기각한다.

- 되돌릴 양이 더 크다. 이미 `api` 경유가 78건이고 `api` 패키지를 가진 도메인이 8개다.
- **ADR-0002·ADR-0003을 뒤집는다.** ADR-0003은 `BatchExecutionLockPort`를 `application.port.out`에서
  `batch.api`로 옮기며 "`terms.api` 선례를 따른다"고 명시했다.
- `application.port.in`은 **계층 이름이지 공개 범위가 아니다.** 내부용 UseCase와 공개 UseCase가
  한 패키지에 섞여, 무엇이 계약인지 이름으로 구분할 수 없다. ArchUnit으로도 가를 수 없다.
- `#349`가 건 `Api mayNotAccessAnyLayer()`와 어긋난다. `application`은 `domain`을 참조하므로
  계약면이 순수 leaf라는 전제가 깨진다.

### (B) 둘 다 허용하는 현행을 유지한다

기각한다. 지금이 그 상태이고 **결과가 측정돼 있다** — 9/10 → 9/25에 `api`는 60 → 78로 늘었는데
우회는 70 → 73으로 줄지 않았다. 규칙이 둘이면 위반 여부를 판정할 수 없고, 판정할 수 없으면
ArchUnit으로 강제할 수 없다. `AGENTS.md` 규칙 2의 유예를 영구화하는 선택이다.

### (C) 별도 계약 패키지·모듈(`contract/`)을 신설한다

kgrzybek의 엄격파 모델이다. 기각한다.

- 단일 Gradle 모듈이라 **컴파일 수준 강제를 얻지 못한다.** ArchUnit으로 막는 것은 `api`와 같고,
  패키지만 하나 더 생긴다.
- 계약의 **소유 도메인이 흐려진다.** ADR-0003이 락을 `common`으로 옮기는 안을 기각한 논리와 같다.

### (D) 공유 enum·에러코드를 `common`에 모은다

기각한다. ADR-0003이 인용한 팀 합의(2026-08-04)는 `common`을 엔티티·리포지토리 수준까지로 한정한다.
`AccountType`·`ProductGroup`은 도메인 어휘이지 공용 인프라가 아니며, `common`에 두면 **누가 바꿀 수
있는지가 사라진다.** 소유 도메인의 `api`에 두면 소유와 공개가 한 자리에서 드러난다.

## 결과

- `AGENTS.md` 규칙 2의 "둘 다 정상 — #359에서 정한다"를 `api` 단일화로 확정한다.
- `README.md`와 ADR-0002의 표현을 같은 계약면을 가리키도록 정리한다. 같은 손질에서 README의
  **`api/` 보유 도메인 목록도 고친다** — "일곱 개"로 적혀 있으나 ADR-0003이 `batch.api`를
  만들어 여덟 개다.
- #350(헥사고날 가이드에 `api/` 규약 문서화)이 이 결론을 받는다.
- 이관 대상 73건의 도메인별 규모를 남긴다 — `transfer` 27 · `product` 20 · `account` 31 중 우회분 ·
  `limit` 7 중 우회분 · `autotransfer` 2 · `scheduledtransfer` 2. 착수 시점에 재측정한다.
