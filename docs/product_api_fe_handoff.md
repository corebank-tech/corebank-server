# 상품 API ↔ FE 연동 인계 문서 (#176 시드 확충 후속)

> **작성**: P3 (상품) · **대상**: FE
> **관련 이슈**: #176(상품 시드 확충), #30(상품 목록), #35(상품 상세), #68(가입 사전 검증)
> **대상 브랜치**: `feat/176-product-seed-expansion`
>
> 상품 시드가 2건 → 12건으로 확충되어 상품몰·상품상세 화면을 실제 API로 붙일 수 있게 됐습니다.
> 이 문서는 **FE 목업과 서버 응답이 어디서 어긋나는지**, 그리고 **각각을 누가 고치는지**를 정리합니다.

관련 문서: [공통 API 규칙](api_conventions.md)

---

## 1. 세 줄 요약

1. 상품 12건(적금 6 / 예금 6)이 시드에 들어갔습니다. FE 목업 `MOCK_PRODUCTS`의 6건은 **이름·금리·기간·금액을 그대로** 서버에 이식했으니 카드 표시값은 동일합니다.
2. FE가 지금 클라이언트에서 하는 **필터·정렬을 서버 파라미터로 옮겨야** 합니다. 기본 `size=10`이라 12건 중 10건만 내려오는데, 그 10건 안에서 다시 정렬하면 틀린 결과가 나옵니다.
3. 상세 화면의 `rates[].primeRate`와 `guide[]`는 서버 모델에 없습니다. **`primeRate`는 FE 계산이 맞고, `guide[]`는 일부만 FE 조립이 가능**합니다 — 자세한 건 §5.

---

## 2. 엔드포인트

| Method | URL | 인증 | 비고 |
| --- | --- | --- | --- |
| GET | `/api/v1/products` | N | 목록. 필터·정렬·페이징 |
| GET | `/api/v1/products/{productId}` | N | 상세 |
| GET | `/api/v1/products/{productId}/terms/{termsId}` | **Y** | 약관 열람 (로그인 필요) |

공통 봉투는 `{ "data": ... }`이고 FE `customFetch`가 벗겨냅니다.

### 2-1. 페이지 응답 필드명 주의

`PageResponse`의 배열 필드는 **`items`** 입니다. `content`가 아닙니다.

```json
{ "page": 0, "size": 10, "totalCount": 12, "totalPages": 2, "items": [ ... ] }
```

---

## 3. 시드 상품 12건 — FE 목업 ↔ 서버 매핑

| FE 목업 id | 상품명 | productCode | productGroup | baseRate | maxRate | 기간 | new |
| --- | --- | --- | --- | --- | --- | --- | --- |
| P001 | 코어 정기예금 | `PRD_CORE_DEP` | DEPOSIT | 3.10 | 3.85 | 6~36 | false |
| P002 | 코어 자유적금 | `PRD_FREE_SAVE` | SAVINGS | 3.70 | 4.20 | 12~36 | true |
| P003 | 코어 목돈예금 | `PRD_LARGE_DEP` | DEPOSIT | 3.50 | 4.05 | 12~60 | false |
| P004 | 코어 정기적금 | `PRD_REGULAR_SAVE` | SAVINGS | 3.80 | 4.35 | 6~24 | false |
| P005 | 코어 단기예금 | `PRD_SHORT_DEP` | DEPOSIT | 2.60 | 3.40 | 1~12 | false |
| P006 | 코어 목표적금 | `PRD_GOAL_SAVE` | SAVINGS | 3.95 | 4.50 | 12~36 | true |
| — | 청년 희망 적금 | `PRD_YOUTH_SAVE` | SAVINGS | 3.20 | 4.50 | 6~36 | true |
| — | 기본 정기예금 | `PRD_BASIC_DEP` | DEPOSIT | 2.80 | 3.30 | 6~36 | false |
| — | 내집마련 적금 | `PRD_HOUSING_SAVE` | SAVINGS | 3.40 | 4.10 | 12~60 | true |
| — | 시니어 우대예금 | `PRD_SENIOR_DEP` | DEPOSIT | 3.00 | 3.60 | 12~36 | false |
| — | 급여이체 적금 | `PRD_SALARY_SAVE` | SAVINGS | 3.60 | 4.40 | 6~24 | true |
| — | 프라임 정기예금 | `PRD_PRIME_DEP` | DEPOSIT | 3.20 | 3.70 | 12~36 | false |

> **`productId`를 하드코딩하지 마십시오.** `product_id`는 `AUTO_INCREMENT`라 DB를 새로 만든 환경에서는 1~12지만, 기존 데이터가 있던 환경에서는 다른 값이 나옵니다. 라우팅·조회에는 **목록 응답이 준 `productId`** 를 쓰고, 특정 상품을 코드로 지목해야 할 때만 `productCode`를 쓰십시오.

### 3-1. 금리 설계 규칙 (시드가 항상 만족)

```
baseRate = 최소 가입기간 구간의 rateTiers[].rate
maxRate  = MAX(rateTiers[].rate) + SUM(preferentialRates[].rate)
```

예 — `PRD_CORE_DEP`: `maxRate 3.85 = 36개월 tier 3.60 + (0.15 + 0.10)`

12건 전부 이 규칙을 만족하는 것을 실서버 조회로 확인했습니다. FE에서 최고금리를 다시 계산해도 카드의 `maxRate`와 어긋나지 않습니다.

---

## 4. 화면별 매핑

### 4-1. C-01 상품목록 (`product-card-grid.tsx`)

| FE 상태 | 서버 파라미터 | 값 |
| --- | --- | --- |
| `filter: "전체"` | `productGroup` 생략 | totalCount 12 |
| `filter: "정기예금"` | `productGroup=DEPOSIT` | totalCount 6 |
| `filter: "정기적금"` | `productGroup=SAVINGS` | totalCount 6 |
| `sort: "rate"` (금리순) | `sort=RATE` | `max_rate DESC` |
| `sort: "latest"` (최신순) | `sort=NEW` | `sale_start_date DESC` |
| — | `sort=NAME` | `product_name ASC` (FE 셀렉트에 없음, 필요하면 추가) |
| — | `page` / `size` | 기본 `page=0&size=10`. **`size`는 허용값 목록** — 아래 |

**필터·정렬을 서버로 넘겨야 하는 이유**: 현재 `product-card-grid.tsx`의 `useMemo`가 받은 배열 안에서 정렬합니다. 서버는 기본 10건만 주므로, 12건 중 상위 10건을 받아 그 안에서 다시 정렬하면 **11·12번째 상품이 영원히 안 보입니다**. 필터도 마찬가지로 페이지 단위로만 걸립니다.

**페이징 UI가 없습니다.** 12건 / `size=10` = 2페이지입니다. 페이지네이션을 붙이거나 `size`를 키우거나, 둘 중 하나는 반드시 필요합니다.

#### `size`는 아무 값이나 못 넣습니다

`ProductQueryService.ALLOWED_PAGE_SIZES`가 **`{5, 10, 20, 30, 50}` 허용값 목록**이고, 여기 없는 값은 `400 CMN0005`(지원하지 않는 페이지 크기입니다)로 떨어집니다.

```
size=10   200   items=10  totalPages=2
size=12   400   CMN0005
size=20   200   items=12  totalPages=1
size=50   200   items=12  totalPages=1
size=100  400   CMN0005
```

한 화면에 12건을 전부 뿌리려면 **`size=20`** 을 쓰십시오. `size=12`나 `size=100`은 400입니다. 상품이 50건을 넘어가면 페이지네이션 외에 방법이 없으니, 지금 붙여두는 편이 낫습니다.

정렬 3종이 실제로 다른 결과를 냅니다 (실서버 응답):

```
sort=RATE p0  코어목표(4.5) 청년희망(4.5) 급여이체(4.4) 코어정기적금(4.35) 코어자유(4.2)
              내집마련(4.1) 코어목돈(4.05) 코어정기예금(3.85) 프라임(3.7) 시니어(3.6)
        p1    코어단기(3.4) 기본정기(3.3)
sort=NEW  p0  급여이체 내집마련 청년희망 기본정기 프라임 코어목표 코어자유 코어정기적금 코어정기예금 코어목돈
sort=NAME p0  급여이체 기본정기 내집마련 시니어 청년희망 코어단기 코어목돈 코어목표 코어자유 코어정기예금
        p1    코어정기적금 프라임
```

`maxRate 4.50`이 코어 목표적금·청년 희망 적금 두 건으로 동률이라, 동률 시 `productCode` 오름차순 보조 정렬이 걸립니다. 순서는 항상 고정입니다.

### 4-2. C-02 상품상세 (`product-detail.tsx`)

목록 응답에는 있는데 **상세 응답에는 없는 필드**가 있습니다. 상세 화면 좌측 요약 카드가 이걸 씁니다.

| FE `ProductDetailData` | 상세 응답 | 상태 |
| --- | --- | --- |
| `name` | `productName` | OK |
| `category` | `productGroup` | `DEPOSIT`→정기예금, `SAVINGS`→정기적금 |
| `maxRate` | `maxRate` | OK |
| `minAmount` / `maxAmount` | `minAmount` / `maxAmount` | OK |
| `notices` | `notices` | OK (상품당 4건) |
| `summary` | **없음** | §5-3 |
| `period` | **없음** (`termOptions`로 유추) | §5-3 |
| `interestMethod` | **없음** | §5-3 |
| `rates[].primeRate` | **없음** | §5-1 |
| `guide[]` | 일부만 | §5-2 |

상세 응답 전문 (`PRD_CORE_DEP`):

```json
{
  "productId": 3, "productCode": "PRD_CORE_DEP", "productName": "코어 정기예금",
  "productGroup": "DEPOSIT",
  "description": "목돈을 정해진 기간 동안 예치하고 만기에 원금과 이자를 함께 받는 거치식 정기예금입니다.",
  "baseRate": 3.1, "maxRate": 3.85,
  "minAmount": 100000, "maxAmount": 500000000, "amountUnit": 100000,
  "termOptions": [6, 12, 24, 36],
  "rateTiers": [
    {"termMonths": 6, "rate": 3.1}, {"termMonths": 12, "rate": 3.45},
    {"termMonths": 24, "rate": 3.55}, {"termMonths": 36, "rate": 3.6}
  ],
  "preferentialRates": [
    {"conditionCode": "PREF_MARKETING", "conditionName": "마케팅 정보 수신 동의", "rate": 0.1},
    {"conditionCode": "PREF_ONLINE", "conditionName": "비대면 채널 가입", "rate": 0.15}
  ],
  "eligibility": "실명의 개인 및 개인사업자",
  "subscriptionRestrictions": ["가입 후 추가 납입은 불가합니다.", "만기 전 중도해지 시 중도해지이율이 적용됩니다."],
  "notices": ["...4건..."],
  "saleStatus": "ON_SALE", "saleEndDate": null,
  "terms": [{"termsId": 4, "termsName": null, "version": null, "required": null, "viewRequired": null, "displayOrder": 1}]
}
```

`terms[]`의 `termsName`·`version`·`required`·`viewRequired`가 null인 것은 **시드 문제가 아니라 서버의 의도된 스텁**입니다 (`ProductDetailResponse.TermsItem.from()` 주석 — P6 `TermsQueryPort` 연동 전까지). 약관 이름이 필요하면 `GET /products/{productId}/terms/{termsId}`(인증 필요)를 쓰십시오.

---

## 5. 어긋나는 3건 — 누가 고치나

### 5-1. `rates[].primeRate` (기간별 우대금리) → **FE가 계산**

FE는 금리안내 탭에 기간별로 다른 우대금리를 그립니다. 서버는 못 줍니다.

```
product_preferential_rate  PK (product_id, condition_code)   ← 기간 축이 없음
subscription               preferential_rate DECIMAL(5,2)    ← 가입 결과도 단일 스칼라
```

`subscription` 테이블이 가입 시점 금리를 `base_rate` / `preferential_rate` / `applied_rate`로 스냅샷하는데, `preferential_rate`가 **기간과 무관한 단일 값**입니다. 즉 "24개월은 우대 0.30%p"라고 화면에 표시한 뒤 가입하면 다른 값이 찍히는 사고가 납니다. 실제 은행 상품에서도 우대금리는 *조건* 단위(급여이체 +0.2%p)지 *기간* 단위가 아닙니다.

**권장**: 전 행 동일한 값으로 표시.

```ts
const primeRate = preferentialRates.reduce((s, r) => s + r.rate, 0)
const rows = rateTiers.map(t => ({
  period: `${t.termMonths}개월`,
  baseRate: t.rate,
  primeRate,
  maxRate: t.rate + primeRate,
}))
```

이 식은 #68 가입 사전 검증의 적용금리 계산(`applied_rate = base_rate + preferential_rate`)과 같은 규칙이라, 조회 화면과 가입 결과가 어긋나지 않습니다.

기간별 우대금리가 **기획상 실제 요구사항**이라면 알려주십시오. 스키마 변경이 필요하고 `subscription`까지 연쇄되므로 별도 이슈로 다뤄야 합니다. 현재 FE 목업이 P001·P002·P004·P005는 기간별로 다르고 P003·P006은 전 행 동일한 걸 보면, 화면 채우기용 값으로 보입니다 — 확인 부탁드립니다.

### 5-2. `guide[]` 5행 → **3행 FE 조립 + 2행 재배치**

| guide 행 | 서버 필드 | 처리 |
| --- | --- | --- |
| 가입대상 | `eligibility` | 그대로 |
| 가입기간 | `termOptions` | `${termOptions[0]}개월 이상 ${termOptions.at(-1)}개월 이하` |
| 가입금액 | `minAmount`/`maxAmount`/`amountUnit` | FE 포맷팅 (`amountUnit`은 배수 검증용, 안내 문구에 넣을지는 FE 판단) |
| 이자지급시기 | 없음 | §5-3 |
| 중도해지 | 없음 | `subscriptionRestrictions`/`notices`가 이미 문장으로 담고 있음 |

**가입기간·가입금액을 서버에 문자열 컬럼으로 넣는 방식은 피했습니다.** `min_term_months` 같은 정형 필드와 진실이 이중화되면, #68 사전 검증은 정형 필드를 쓰는데 화면은 문자열을 보게 되어 "화면엔 6개월부터라는데 검증은 12개월부터"가 나옵니다.

"중도해지" 행은 이미 아래 문장이 내려갑니다. 상품안내 탭에서 빼고 유의사항 탭에서 소화하는 걸 권합니다.

- `subscriptionRestrictions`: `"만기 전 중도해지 시 중도해지이율이 적용됩니다."`
- `notices`: `"만기 전 중도해지 시 약정이율보다 낮은 중도해지이율이 적용되어 이자가 줄어듭니다."`

### 5-3. `summary` · `period` · `interestMethod` → **서버 DTO 누락, 서버가 고침**

이건 FE 잘못이 아니라 **`ProductDetailResponse`의 필드 누락**입니다. 컬럼은 DB에 이미 다 있고 DTO에만 안 실려 있습니다.

| 필드 | DB 컬럼 | 현재 |
| --- | --- | --- |
| `summary` | `product.summary` | 목록 응답에만 있음 |
| `minTermMonths` / `maxTermMonths` | `product.min_term_months` / `max_term_months` | 목록 응답에만 있음 |
| `interestPayType` | `product.interest_pay_type` | 어느 응답에도 없음 |

**서버 쪽에서 상세 응답에 이 3종을 추가하겠습니다.** 그때까지 FE는 목록에서 받은 값을 상세로 넘기거나 `termOptions`로 대체하십시오.

한 가지 주의: `interestPayType`은 `SIMPLE`/`COMPOUND`(**단리/복리 = 이자 계산 방식**)이고, FE의 `interestMethod` "만기일시지급식"은 **지급 시기**입니다. 서로 다른 축이라 그대로 매핑하면 안 됩니다. 지금 FE는 전 상품 "만기일시지급식" 고정값이라 실질 정보가 없으니, 라벨을 **"이자계산방식: 단리 / 복리"** 로 바꾸는 걸 권합니다. 지급 시기가 정말 필요하면 컬럼 추가를 별도로 논의하겠습니다.

---

## 6. 액션 아이템

### FE

- [ ] 필터·정렬을 클라이언트 `useMemo`에서 서버 파라미터(`productGroup`/`sort`)로 이관
- [ ] 페이지네이션 추가 또는 `size=20` 명시 (12건 / 기본 10건 = 2페이지. `size`는 `{5,10,20,30,50}`만 허용 — §4-1)
- [ ] `PageResponse`의 배열 필드명이 `items`인 것 확인 (`content` 아님)
- [ ] `ProductCard.id`를 `"P001"` 문자열 → 서버 `productId`(숫자)로 교체, 하드코딩 금지
- [ ] `rates[].primeRate`를 `preferentialRates` 합으로 계산 (§5-1)
- [ ] `guide[]`를 서버 필드 조립 방식으로 재구성 (§5-2)
- [ ] `interestMethod` 라벨을 "이자계산방식(단리/복리)"로 변경 검토 (§5-3)
- [ ] 기간별 우대금리가 기획 요구사항인지 회신 (§5-1)

### BE (P3)

- [x] 상품 시드 12건 확충 — #176
- [ ] `ProductDetailResponse`에 `summary`·`minTermMonths`·`maxTermMonths`·`interestPayType` 추가 (§5-3)
- [ ] `openapi.yaml`을 레포에 반영 — FE `orval.config.ts`가 이 파일을 단일 계약 출처로 보고 있고, 없으면 `pnpm codegen`이 의도적으로 실패합니다
- [ ] `terms[]` 스텁 해소 — P6 `TermsQueryPort` 연동

---

## 7. 로컬 확인 방법

```bash
docker compose up -d minicore-mysql minicore-redis
./gradlew bootRun --args='--spring.profiles.active=local'

# 목록 — 정렬 3종 / 필터 / 2페이지
curl 'http://localhost:8080/api/v1/products?sort=RATE&page=0&size=10'
curl 'http://localhost:8080/api/v1/products?sort=NEW&page=0&size=10'
curl 'http://localhost:8080/api/v1/products?productGroup=SAVINGS&sort=NAME'

# 상세
curl 'http://localhost:8080/api/v1/products/3'
```

> **여러 서버 인스턴스가 같은 `minicore` DB를 공유하면 시드가 서로 덮어씁니다.** `R__seed_master_data.sql`은 Repeatable 마이그레이션이라 체크섬이 바뀔 때마다 재실행되는데, 구버전 파일을 가진 인스턴스가 기동하면 그 버전으로 되돌려 씁니다. R은 삭제를 하지 않으므로 상품 건수는 그대로인데 값만 옛것으로 바뀌어 원인 파악이 어렵습니다. 브랜치를 바꿔 띄울 때는 다른 인스턴스를 내리십시오.
