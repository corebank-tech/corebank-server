# PH-18 · 고객 개인정보 암호화 키 주입 준비

## 상태

이 문서는 #736의 암호화 키 주입 및 DB 확장 단계를 설명합니다.

Flyway `V202610261600__add_customer_pii_shadow_columns.sql`을 통해
`customer` 테이블에 다음 컬럼을 추가했습니다.

- `email_ciphertext`
- `phone_number_ciphertext`
- `email_search_hash`

기존 `email`, `phone_number` 컬럼 및 UNIQUE 제약은 유지하며,
신규 컬럼은 데이터 전환이 완료되기 전까지 NULL을 허용합니다.

현재 JPA 저장·조회 경로는 기존 평문 컬럼을 사용합니다.
`app.pii.enabled=true`로 암호화 Bean을 생성하더라도
고객 데이터가 자동으로 암호화되지는 않습니다.

## 환경변수 계약

| 환경변수 | 의미 | 초기값 |
|---|---|---|
| `APP_PII_ENABLED` | 암호화 관련 빈 생성 활성화 | 미설정(비활성) |
| `APP_PII_AES_KEY_BASE64` | AES-256-GCM용 32바이트 키의 표준 Base64 인코딩 | 없음 |
| `APP_PII_HMAC_KEY_BASE64` | 이메일 정확 일치 검색용 HMAC-SHA256 32바이트 키의 표준 Base64 인코딩 | 없음 |

- 두 키는 **서로 다른**, 암호학적으로 안전하게 생성한 32바이트 난수여야 합니다.
- 서버에서 임시 키를 생성하거나 키가 없을 때 평문 저장으로 조용히 전환하지 않습니다.
- 키 원문·Base64 문자열을 레포지토리, `.env` 추적 파일, PR, 로그, 예외 메시지에 남기지 않습니다.
- 프로덕션과 전환 대상 VM이 같은 데이터를 읽으려면 **동일한 해당 환경의 키 버전**을 사용할 수 있어야 합니다. 키 전달 방식은 PH-72(Secrets Manager), PH-70(복원·VM)과 함께 확정합니다.
- AES 키를 교체하면 이전 암호문은 이전 키 없이는 복호화할 수 없습니다. HMAC 키를 교체하면 기존 `email_search_hash`가 더 이상 검색에 맞지 않으므로 전체 재계산이 필요합니다. **암호화 데이터 마이그레이션 완료 전에는 키를 교체하지 않습니다.**

## 전환 순서 및 주의

1. 기존 평문 이메일 중복 확인과 관리자 검색 성능 기준선을 먼저 수집합니다.
2. 신규 암호문 및 검색 해시 컬럼을 추가하고, JDBC 시드/기존 데이터 백필 경로를 확정합니다.
3. 양쪽 읽기·쓰기 호환성, 유일 제약, 백필 재실행 및 복구 경계를 검증합니다.
4. 모든 고객 행의 암호화·검색이 확인된 뒤 평문 컬럼을 제거합니다.

**2~4 단계가 끝나기 전까지 이 구성의 활성화를 운영 DB 암호화 완료로 간주하면 안 됩니다.**
키 없이 복호화를 시도하거나 암호화 기능이 켜진 상태에서 키가 틀리면 실패해야 합니다.

## 키 주입 설정 단위 테스트

```bash
./gradlew test --tests '*CustomerPiiCryptoConfigurationTest' \
  --tests '*CustomerPiiCipherTest' \
  --tests '*CustomerEmailBlindIndexTest'
```

## AES-GCM 암호문 인증 범위

암호문은 버전별 형식으로 저장하며, AES-GCM의 AAD에 컬럼 식별자를 포함합니다.

- 이메일: `corebank:customer:email:v1:`
- 휴대폰 번호: `corebank:customer:phone_number:v1:`

따라서 이메일 암호문을 휴대폰 번호 컬럼에서 복호화할 수 없습니다.

현재 AAD에는 고객 ID가 포함되지 않아 서로 다른 고객 행 사이의
암호문 교체까지 방지하지는 못합니다.

고객 ID를 AAD에 포함할지는 신규 고객의 ID 생성 시점 및
기존 데이터 백필 절차를 고려해 실제 JPA 저장 연동 전에 결정합니다.
