# 이의제기 관리자 API와 고객 API 계약

지급정지 이의제기 AI 심사 지원([D-36](README.md#d-36--113--리드))의 관리자 심사 화면과 고객 비대면 신청 화면이 부르는 API다.
Agent 워커가 부르는 내부 API는 [objection_agent_contract.md](objection_agent_contract.md)에 있다. 이 문서가 계약 정본이고, 바꿀 때는 이 문서와 서버 구현을 함께 고친다.

## 1. 공통 규칙

- 성공은 HTTP 200 + `code="0000"`. 오류는 `PST` 코드다([api_conventions.md](../api_conventions.md) §4-18).
- 상태를 바꾸는 POST는 모두 `Idempotency-Key`가 필수다(관리자, 고객 모두). 같은 키로 다시 보내면 처음 응답을 그대로 돌려준다. multipart 요청의 지문에는 파일 본문 대신 파일 이름과 SHA-256이 들어간다.
- 시각은 KST `LocalDateTime` ISO 문자열, 날짜는 `LocalDate`, 금액은 원 정수다.
- 관리자 경로 권한: 조회는 `PAYMENT_STOP_READ`, 변경은 `PAYMENT_STOP_WRITE`. 판정 승인과 반려만 `PAYMENT_STOP_APPROVE`이고, 심사 시작 기록은 조회 권한으로 부른다([admin_auth.md](admin_auth.md)).
- 고객 경로는 로그인 세션과 CSRF 헤더가 필요하다. 본인 계좌의 사건만 다룬다.

## 2. 상태와 기한

`objectionStatus`

| 값 | 뜻 | 할 수 있는 일 |
|---|---|---|
| `RECEIVED` | 접수. 소명 완비 확인 전 | 완비 확인, 보완 요청, 불수용, 외부 확인 |
| `UNDER_REVIEW` | 소명 완비 확인됨. 심사 기한 진행 중 | 판정 작성(전부), 보완 요청, 외부 확인 |
| `SUPPLEMENT_REQUESTED` | 보완 요청됨. 보완 기한 진행 중 | 고객 보완 제출, 불수용(보완 기한 경과 불수용 포함), 외부 확인 |
| `SUPPLEMENT_SUBMITTED` | 고객이 보완 자료를 냄 | 완비 재확인, 2회째 보완 요청, 불수용, 외부 확인 |
| `PENDING_APPROVAL` | 수용 또는 일부 지급정지 판정이 승인 대기 | 승인, 반려, 외부 확인 |
| `DECIDED` | 확정 | 조회만 |

판정 행 `status`: `PENDING_APPROVAL`, `CONFIRMED`, `RETURNED`.

- 막는 쪽(불수용, 보완 요청, 외부 확인)은 확정 전 언제나 열려 있고(D9), 보류를 줄이는 판정(수용, 일부 지급정지)은 `UNDER_REVIEW`에서만 쓴다. 맞지 않는 상태면 `PST0308`, 확정 뒤면 `PST0302`다.
- 심사 기한(`reviewDeadline`) = 소명 완비 확인일 + 5영업일. 영업일은 `business.api`의 `nextBusinessDay`를 반복해 센다. 휴일에 확인해도 첫 다음 영업일이 1일째다.
- 보완 기한(`supplementDue`) = 1회째 요청일 + 5영업일, 2회째 + 3영업일. 3회째 요청은 `PST0006`이다.
- 보완을 요청하면 심사 기한이 멈춘다(`reviewDeadline`이 null). 고객이 보완을 내면 담당자가 완비를 다시 확인하고, 그때 기한을 새로 건다.
- `deadlineAlert`: 확정 전이고 오늘이 심사 기한 하루 전 영업일 이후면 true(설계 불변식 6).
- `supplementOverdue`: `SUPPLEMENT_REQUESTED`이고 오늘이 보완 기한 뒤면 true. 목록의 불수용 대상 표시이고 자동 확정은 없다(D32).

## 3. 관리자 API

경로 앞은 모두 `/admin/objections`다.

| API | 권한 | 본문 | 응답 data |
|---|---|---|---|
| `GET /` | READ | | 목록(아래 Summary) |
| `GET /{id}` | READ + `X-Access-Reason` | | 상세(아래 Detail). 조회 감사 |
| `POST /` | WRITE | 기존 JSON(`caseId`, `clause`, `statement`, `simplifiedRequested`, `idCopyConfirmed`, `litigationPending`) | `{ objectionId }`. 영업점 대리 접수. 분석 1건과 명의인 접수 통지를 함께 남긴다. 해제된 사건은 `PST0201` |
| `POST /{id}/completeness` | WRITE | 없음 | `{ reviewDeadline }`. `RECEIVED`, `SUPPLEMENT_SUBMITTED`에서만, 아니면 `PST0308` |
| `POST /{id}/supplement-requests` | WRITE | `{ reason(≤500), missingDocuments: string[](≤20, 각 ≤100) }` | `{ round, supplementDue }`. 명의인 통지 기록. `PST0006`, `PST0302`, `PST0308` |
| `POST /{id}/review-sessions` | READ | 없음 | `{ sessionId, startedAt }`. 상세 화면을 연 시각 |
| `POST /{id}/decisions` | WRITE | `{ result, reason(≤500), earlyReleaseBasis(≤500) }` | Decision. `REJECTED`는 바로 `CONFIRMED`이고 지급정지 유지. `ACCEPTED`, `PARTIAL`은 계좌를 건드리지 않고 `PENDING_APPROVAL`. 게이트가 열지 않으면 `PST0301` |
| `POST /{id}/decisions/{decisionId}/approve` | APPROVE | 없음 | Decision(`CONFIRMED`). 작성자와 같으면 `PST0309`. 확정 직전 게이트를 락 밖에서 다시 계산하고(D11) 열리지 않으면 `PST0301`(판정은 승인 대기로 남는다). 동시 승인은 하나만 확정되고 나머지는 `PST0302` |
| `POST /{id}/decisions/{decisionId}/return` | APPROVE | `{ reason(≤500) }` | Decision(`RETURNED`, `returnReason`). 이의제기는 `UNDER_REVIEW`로 돌아가고 심사 기한은 그대로다 |
| `POST /{id}/external-checks` | WRITE | `{ agency: POLICE \| PEER_BANK, mockReply: INVOLVEMENT_SUSPECTED \| DOCUMENT_FORGED \| OWNER_UNRELATED \| NO_RECORD }` | ExternalCheck. 모의라 요청 즉시 `REPLIED`. 같은 기관에 이미 요청했으면 그 행을 그대로 돌려준다(회신 값도 처음 것). 확정 뒤 새 요청은 `PST0302` |
| `GET /{id}/external-checks` | READ | | ExternalCheck 배열 |
| `GET /{id}/notices` | READ | | `[{ noticeId, target, kind, channel, basis, recordedAt }]` |
| `GET /{id}/ledger` | READ | | `[{ transactionNumber, direction, interbank, counterpartyAccountId, amount, occurredAt, disputed, afterDispute }]`. 게이트 창 기간의 지급정지 계좌 원장 |
| `POST /{id}/documents` | WRITE | multipart: `files`(1~10개), `kind`(`EVIDENCE` \| `ID_COPY`) | Document 배열(올린 것). `round`는 지금까지 보완 요청 횟수다. `PST0007`, `PST0302` |
| `GET /{id}/documents` | READ | | Document 배열. 신분증 사본도 `kind=ID_COPY`로 보인다 |
| `GET /{id}/documents/{documentId}/content` | READ + `X-Access-Reason` | | 파일 바이트(`ApiResponse` 아님). `Content-Disposition: attachment`, `X-Content-Type-Options: nosniff`, `Cache-Control: no-store`. 조회 감사(`OBJECTION_DOCUMENT_VIEW`). `PST0205` |
| `GET /{id}/documents/{documentId}/ocr` | READ | | `{ documentId, status, engine, pages, updatedAt }`. 텍스트는 가명 처리한 값. OCR 전이면 `status=PENDING`, `pages=[]`. 신분증 사본은 OCR 하지 않아 `PST0205` |
| `GET /{id}/agent-runs`, `/agent-runs/{runId}/steps`, `/review-draft`, `POST /review-requests` | | | 기존([objection_agent_contract.md](objection_agent_contract.md) §5). `after` 생략 시 전체 단계 |

**Summary** (목록 행): `objectionId`, `caseId`, `accountId`, `clause`, `objectionStatus`, `caseStatus`, `disputedTotal`, `objectionDeadline`, `receivedAt`, `receivedChannel`(`BRANCH` / `ONLINE`), `reviewDeadline`, `deadlineAlert`, `supplementDue`, `supplementOverdue`, `latestRunStatus`(실행 없으면 null), `latestRunLastStepTitle`(단계 없으면 null). 실행 상태와 마지막 단계를 목록 조회 한 번에 함께 준다.

**Detail** (상세): 기존 필드(사건, 다툼 거래, 소명, `gate`) + `receivedChannel`, `completenessConfirmedAt`, `reviewDeadline`, `deadlineAlert`, `supplementDue`, `supplementRound`, `supplementOverdue`, `supplementRequests`(`[{ round, reason, missingDocuments, dueDate, requestedAt, submittedAt, submitNote }]`), `decision`(확정된 판정 또는 null), `pendingDecision`(승인 대기 판정 또는 null), `externalChecks`, `documentCount`. `gate`는 조회 시점 원장과 외부 확인 회신으로 다시 계산한다.

**Decision**: `decisionId`, `result`, `status`, `reason`, `earlyReleaseBasis`, `requestedHoldAmount`, `appliedHoldAmount`(확정된 `PARTIAL`만), `decidedBy`(작성자), `decidedAt`(작성 시각), `approvedBy`, `approvedAt`, `returnReason`.

**ExternalCheck**: `checkId`, `agency`, `status`, `reply`, `requestedAt`, `repliedAt`. 부정 회신(`INVOLVEMENT_SUSPECTED`, `DOCUMENT_FORGED`)은 게이트 순서 1(불수용 후보), `OWNER_UNRELATED`는 순서 4(수용 후보), `NO_RECORD`는 게이트에 영향이 없다.

**Document**: `documentId`, `kind`, `fileName`, `contentType`, `size`, `sha256`, `round`, `uploaderRole`(`HOLDER` / `ADMIN`), `uploadedAt`, `ocrStatus`(증빙만, 신분증 사본은 null).

## 4. 고객 API

| API | 본문 | 응답 data |
|---|---|---|
| `GET /payment-stops/my` | | `[{ caseId, accountNumberMasked, caseStatus, disputedTotal, objectionDeadline, objectionId, objectionStatus, supplementDue, missingDocuments, decisionResult }]`. 해제된 사건도 결과 확인용으로 보인다 |
| `POST /payment-stops/{caseId}/objections` | multipart: `clause`(1~3), `statement`(≤4000), `simplifiedRequested`, `litigationPending`(문자열 `true`/`false`, 생략하면 false), `idCopy`(파일 1개, 필수), `evidence`(0~9개) | `{ objectionId }` |
| `POST /objections/{objectionId}/supplements` | multipart: `evidence`(1~9개), `note`(≤1000, 생략 가능) | `[{ documentId, kind, fileName, contentType, size, round, uploadedAt }]` |

- `objectionId`, `objectionStatus`는 아직 신청하지 않았으면 null이다. `supplementDue`와 `missingDocuments`는 `SUPPLEMENT_REQUESTED`일 때만 값이 있고 그 밖에는 null과 빈 배열이다. `decisionResult`는 확정된 심사결과이고 확정 전이면 null이다.
- 신청: 본인 계좌의 지급정지 사건이 아니면(남의 사건, 없는 사건, 해제된 사건) 모두 `PST0201`이다(존재 여부 비노출, AGENTS 규칙 4). 신청 기한이 지났으면 `PST0002`, 신분증 사본이나 사유가 없으면 `PST0003`, 파일 위반은 `PST0007`, 이미 신청한 사건은 `PST0304`. 파일을 먼저 모두 검사하므로 위반이면 접수가 남지 않는다.
- 신청하면 같은 트랜잭션에서 접수(`receivedChannel=ONLINE`), 신분증 사본(암호화), 증빙, 분석 1건, 명의인 접수 통지를 남긴다.
- 보완 제출: 본인 이의제기가 아니면 `PST0202`, `SUPPLEMENT_REQUESTED`가 아니면 `PST0308`. 제출하면 `SUPPLEMENT_SUBMITTED`가 되고 분석 1건이 들어간다. 소명 완비는 담당자가 다시 확인한다.
- 파일 개수 10개와 합계 30MB 한도는 신분증 사본을 포함해 이의제기 전체로 센다.

## 5. 파일 저장

- 파일 본문은 `app.payment-stop.documents.root`(환경변수 `PAYMENT_STOP_DOCUMENT_ROOT`, 기본 `./data/objection-documents`, `.gitignore` 대상) 아래 `{objectionId}/{documentId}`에 둔다. DB(`objection_document`)에는 키, 해시, 종류, 크기만 쓴다.
- 신분증 사본은 AES-256-GCM(파일마다 12바이트 IV)으로 암호화한다. 키는 `PAYMENT_STOP_DOCUMENT_KEY`(32바이트 base64)이고 저장소에 두지 않는다. 키가 없으면 신분증 사본을 받지 못한다.
- 형식은 매직 바이트로 PDF, PNG, JPEG만 받는다. 요청의 `Content-Type`과 확장자는 보지 않는다. 파일 이름은 경로와 제어 문자를 떼고 저장한다.
- 신분증 사본은 Agent 내부 API의 목록, 내용, OCR에서 모두 빠진다(D37). 관리자 열람은 조회 사유와 감사를 남긴다.
- S3 이전(D36, PH-15)은 `DocumentStoragePort` 뒤의 구현만 바꾼다.

## 6. 통지 기록(D30)

실제로 보내지 않고 `objection_notice`에 기록만 한다. 명의인은 `SMS`, 그 밖은 `DOCUMENT`다.

| 시점 | 기록 |
|---|---|
| 접수(대리 접수, 고객 신청) | `HOLDER` `RECEIPT` |
| 보완 요청 | `HOLDER` `SUPPLEMENT_REQUEST` |
| 수용 승인 | `VICTIM`, `FSS` `DECISION_ACCEPTED` + 지급정지 종료라 `HOLDER`, `VICTIM`, `RELATED_BANK` `STOP_TERMINATED` |
| 일부 지급정지 승인 | `VICTIM`, `FSS` `DECISION_PARTIAL` |
| 불수용 확정 | `HOLDER` `DECISION_REJECTED` |

## 7. 감사

| 이벤트 | 남는 때 |
|---|---|
| `OBJECTION_DECISION_SUBMIT` | 수용, 일부 지급정지 작성(승인 대기) |
| `OBJECTION_DECISION_CONFIRM` | 불수용 작성, 수용과 일부 지급정지 승인(승인자 이름으로, detail에 `decidedBy`) |
| `OBJECTION_DECISION_RETURN` | 반려 |
| `PAYMENT_STOP_RELEASE` | 승인으로 계좌가 해제되거나 일부 지급정지로 바뀜 |
| `OBJECTION_DETAIL_VIEW`, `OBJECTION_DOCUMENT_VIEW` | 상세 조회, 서류 열람(조회 사유 포함) |
