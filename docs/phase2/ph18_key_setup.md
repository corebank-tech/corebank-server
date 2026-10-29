# PH-18 고객 개인정보 암호화 운영 가이드

관련 이슈: #736 · PH-72(Secrets Manager) · PH-70-②(RDS 컷오버)

## 1. 적용 범위

`customer.email`, `phone_number`를 AES-256-GCM으로 암호화하고 이메일 정확 검색에 HMAC-SHA256 해시를 사용한다.

- 신규 컬럼: `email_ciphertext`, `phone_number_ciphertext`, `email_search_hash`
- 암호문: `v1:` + Base64URL, 랜덤 nonce 및 컬럼별 AAD 적용
- 기존 평문 컬럼과 API 응답 계약은 전환 완료까지 유지
- 평문 컬럼 제거는 별도 후속 작업

## 2. 설정 및 키 관리

| 환경 변수 | 용도 | 기본값 |
|---|---|---|
| `APP_PII_ENABLED` | 평문·암호문 이중 쓰기 | `false` |
| `APP_PII_READ_ENCRYPTED` | 암호문 조회·해시 검색 | `false` |
| `APP_PII_AES_KEY_BASE64` | AES-256-GCM 32바이트 키 | 없음 |
| `APP_PII_HMAC_KEY_BASE64` | HMAC-SHA256 32바이트 키 | 없음 |

- AES/HMAC 키는 서로 달라야 하며, 누락·오설정 시 암호화 기능 시작에 실패한다.
- 키는 Git·설정 파일·Terraform state·로그에 저장하지 않는다.
- 운영 키는 PH-72 Secrets Manager에서 관리한다. WAS 2대와 백필·검증 실행기는 동일한 키를 사용해야 한다.
- 기존 암호문이 존재하는 동안 임의로 키를 교체하거나 자동 회전하지 않는다.
- 설정 변경 시 WAS 재시작·재배포가 필요하다.

## 3. 운영 전환 순서

| 단계 | 설정·작업 |
|---|---|
| ① 코드 배포 | `APP_PII_ENABLED=false`, `APP_PII_READ_ENCRYPTED=false` |
| ② 키 주입 | PH-72 연동 후 WAS 2대에 동일 키 주입 |
| ③ 이중 쓰기 | 양쪽 WAS에 `APP_PII_ENABLED=true` 적용 |
| ④ 백필 | 신규 RDS에서 일회성 백필 실행 |
| ⑤ 전체 검증 | 암호문 누락·복호화·HMAC 불일치 모두 0건 확인 |
| ⑥ 조회 전환 | 양쪽 WAS에 `APP_PII_READ_ENCRYPTED=true` 적용 |
| ⑦ 회귀 검증 | 회원가입·로그인·연락처 변경·관리자 검색 확인 |

모든 WAS의 이중 쓰기 활성화가 완료된 후 백필한다. 운영 전환 전 팀 승인 및 백업·복구 경로를 확인한다.

## 4. 일회성 백필·검증

운영 도구: `CustomerPiiMaintenanceApplication`

- `app.pii.maintenance.action=backfill`: 누락된 암호화 컬럼 백필
- `app.pii.maintenance.action=verify`: 전체 고객 암호문·평문·해시 검증
- 백필은 최대 500건씩 별도 트랜잭션으로 처리하며 `SELECT ... FOR UPDATE`를 사용한다.
- 기존 암호문은 보존하고 누락된 컬럼만 채운다.
- 실행 대상 DB 호스트·스키마를 확인하며, 유지보수 모드에서는 Flyway를 실행하지 않는다.

로컬 전체 검증 예시(동일 PowerShell에 로컬 키 주입 필요):

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local,pii-maintenance --app.pii.enabled=true --app.pii.read-encrypted=false --app.pii.maintenance.action=verify --app.pii.maintenance.expected-db-host=localhost --app.pii.maintenance.expected-db-name=minicore"
```

운영에서는 실제 RDS 설정과 운영 키를 사용한다. COB 및 성능 측정 시간대와 겹치지 않도록 실행 일정을 협의한다.

## 5. 검증 및 장애 대응

**로컬 검증 (2026-10-29)**

- 전체 Gradle 테스트 통과
- 백필 동시성 및 재실행 테스트 통과
- 로컬 고객 5명 전수 검증: 누락·복호화 불일치·HMAC 불일치·오류 모두 0건

운영 RDS 백필·검증과 조회 성능 측정은 아직 수행하지 않았다.

**장애 대응**

- 키 오류: 기존 키 복원. 새 키 임의 생성 금지.
- 백필 중단: 원인 확인 후 재실행하고 전체 검증 수행.
- 검증 불일치: 암호문 조회 전환 중단.
- 조회 전환 후 장애: `APP_PII_ENABLED=true`를 유지하면서 `APP_PII_READ_ENCRYPTED=false`로 조회 롤백 검토.
- 평문 컬럼은 안정화 확인 전 제거하지 않는다.
