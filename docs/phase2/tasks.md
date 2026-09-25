# 2차 태스크 레지스트리

> **이 파일이 정본인 것** — 2차 태스크 하나하나의 소유자·스프린트·인일·선행·기한·범위·완료 기준·트랙 간 계약(인터페이스 시그니처, 머지 순서, 출력값).
> **정본이 아닌 것** — 공통 규칙·달력·지표·범위 밖·결정 이력은 [README.md](README.md), 용어 이름은 [glossary.md](glossary.md).

**코드 현황 기준:** `dev` `5f41d5ce` (2026-09-23). 각 절의 "현황" 줄은 이 시점 실측이다.

## 사용 규칙

- **태스크 ID는 바꾸지 않는다.** `PH-xx`는 기획 번호이고, FE는 화면 ID(`B-9`, `C-1`, `ADM-04` …)를 쓴다. 쪼갠 태스크는 `-①`·`-②`를 붙인다.
- **한 사실은 한 곳에만 적는다.** 소유자·인일·기한은 §1 표에만 둔다. §2 상세 절은 범위·계약·완료 기준만 적는다.
- **태스크를 끝내는 PR에서 이 파일도 함께 고친다.** §1 표의 상태 칸과 §2의 "현황" 줄이 대상이다. 인일이나 스프린트를 바꾸면 [README.md §4](README.md#4-트랙과-인일)의 합계도 같은 PR에서 고친다.
- **소유자 = 구현하고 머지 책임을 지는 사람.** 다른 트랙이 쓰는 인터페이스는 제공 태스크 절에 시그니처를 적는다. 쓰는 쪽은 그 절을 링크만 한다.
- 담당 표기: P1 주유나 `YounaJ00` · P2 정선우 `vsopsw` · P3 김다연 `danhandev`(기획 리드) · P4 이건 `astrokan` · P5 류재성 `bprosefan` · P6 장영훈 `cy389`

상태 값 — `완료` · `진행`(PR 열림 또는 착수) · `예정` · `조건부`(선행이나 여유가 생겨야 착수) · `대체`(결정으로 다른 태스크가 대신한다. 인일은 대신하는 태스크로 넘어간다).

---

## 1. 레지스트리

스프린트 — **S1** 9/21~10/2 · **S2** 10/6~10/16 · **S3** 10/19~10/30 · **S4** 11/2~11/6 (새 BE 기능 금지). S0(9/16~18)은 인일 밖이다.

### P1 — 알림 · 이벤트·아웃박스 · 관리자 인증·감사 · 품질 측정 · FE 리드

| ID | 제목 | S | 인일 | 선행 | 기한 | 이슈·PR | 상태 |
|---|---|---|---|---|---|---|---|
| [PH-32](#ph-32-도메인-이벤트-발행-골격) | 도메인 이벤트 발행 골격 | S1 | 2 | — | **9/30** | #436 | 예정 |
| [PH-30](#ph-30-k6-harness--이체-tps--동시성-정합성-s1-베이스라인--s4-재측정) | k6 harness · TPS·p95·p99 · 동시성 정합성 베이스라인 | S1 | 4 | PH-60 (9/30) | harness **10/2** · 베이스라인 **10/8** | #385 · #384 | 예정 |
| [PH-01](#ph-01-알림-도메인-인수--영속성) | 알림 도메인 인수 · 영속성 | S1 | 3 | — | — | #389 | 예정 |
| [PH-49a-①](#ph-49a-관리자-인증-①-결정스키마-102--②-구현-108) | 관리자 인증 — 결정 · 스키마 · Flyway | S1 | 1 | — | **10/2** | — | 예정 |
| [PH-03-①](#ph-03-api-문서-전수-점검-①-측정-s1--②-0-마감-s4) | API 문서 전수 점검 — 측정 | S1 | 0.5 | — | — | #477 · PR #476 | 완료 |
| [FE-SNAP](#fe-snap-codegen-스냅샷) | codegen 스냅샷 | S1 | 0.5 | — | — | — | 예정 |
| 리드 | FE 리드 조율 | S1 | 0.5 | — | — | — | — |
| [PH-49a-②](#ph-49a-관리자-인증-①-결정스키마-102--②-구현-108) | 관리자 인증 구현 | S2 | 3 | PH-49a-① | **10/8 머지** | — | 예정 |
| [PH-96-①](#ph-96-transactional-outbox-①-발행측릴레이-s2--②-구독-s3) | Transactional Outbox — 발행측 · 릴레이 | S2 | 4 | PH-32 · PH-99 · EVT-2 | 유실 재현 10/8 | #437 | 예정 |
| [PH-89](#ph-89-감사-5w--조회-감사--조회-사유-강제) | 감사 5W · 조회 감사 · 조회 사유 강제 | S2 | 2.5 | PH-49a-② | 10/16 | #458 | 예정 |
| [FE #136](#fe-136-관리자-세션-정책--로그인-훅-교체) | 관리자 세션 정책 · 로그인 훅 교체 | S2 | 0.25 | PH-49a-② | 10/16 릴리스 전 | FE #136 | 예정 |
| 리드 | FE 리드 조율 | S2 | 0.5 | — | — | — | — |
| [PH-04](#ph-04-알림-조회-api--마스킹-통일) | 알림 조회 API · 마스킹 통일 | S3 | 3 | PH-01 | — | #390 #391 #392 #410 | 예정 |
| [PH-96-②](#ph-96-transactional-outbox-①-발행측릴레이-s2--②-구독-s3) | 아웃박스 구독 — 알림 4종 | S3 | 1.5 | PH-96-① · PH-04 | — | #393 #394 #395 #396 | 예정 |
| [PH-06](#ph-06-지연이체-서비스) | 지연이체 서비스 | S3 | 4 | PH-40 · PH-99 · PH-80 · [O-09](README.md#8-미결) | — | — | 예정 |
| [PH-08](#ph-08-레이트리밋) | 레이트리밋 (로그인·OTP·이체) | S3 | 3.5 | — | — | #332 | 예정 |
| [PH-49b](#ph-49b-직무분리-부여회수--maker-checker--step-up-표기) | 직무분리 부여/회수 · maker-checker · Step-up 표기 | S3 | 3 | PH-49a-② · PH-92 | — | — | 예정 |
| [PH-82](#ph-82-worm-감사-저장소--무결성-해시) | WORM 감사 저장소 · 무결성 해시 | S3 | 2.5 | PH-72 버킷 (10/21) · PH-89 · 해시 컬럼 위치 P4 합의 | — | — | 예정 |
| [FE B-9](#fe-b-9-timeout-화면--재시도-차단) | TIMEOUT 화면 — 재시도 차단 | S3 | 0.5 | PH-80 (10/8) | — | — | 예정 |
| [FE C-7](#fe-c-7-알림함-실연동) | 알림함 실연동 | S3 | 0.5 | PH-04 | — | — | 예정 |
| [FE C-5](#fe-c-5-correlation-id-헤더-전파) | correlation id 헤더 전파 | S3 | 0.5 | PH-42 | — | — | 예정 |
| [P1-FINAL](#p1-final-최종-측정--품질기록-리포트) | 1만 건 주입 · 강제 종료 · 품질·기록 리포트 | S4 | 3 | 전 태스크 | 11/6 | — | 예정 |
| [PH-30](#ph-30-k6-harness--이체-tps--동시성-정합성-s1-베이스라인--s4-재측정) | TPS · 동시성 재측정 (P4 락 튜닝 후 포함) | S4 | 0.5 | PH-30 S1 · 락 튜닝 | — | #384 | 예정 |
| [PH-03-②](#ph-03-api-문서-전수-점검-①-측정-s1--②-0-마감-s4) | 미문서화 API 0 마감 | S4 | 0.5 | PH-03-① | — | — | 예정 |
| [PH-93](#ph-93-sast--sca-ci-편입--조건부) | SAST · SCA CI 편입 | S4 | 1.5 | PH-55 | — | — | 조건부 |
| 리드 | FE 리드 조율 | S4 | 0.5 | — | — | — | — |

### P2 — 예적금 생명주기 · 이자 · 출금가능액 · 데이터 계층 · 엣지·접근통제·DR

| ID | 제목 | S | 인일 | 선행 | 기한 | 이슈·PR | 상태 |
|---|---|---|---|---|---|---|---|
| [PH-10](#ph-10-계좌-상태-전이-매트릭스--flyway) | 계좌 상태 전이 매트릭스 + Flyway | S1 | 3 | — | **Flyway 9/30 전** | #419 | 예정 |
| [PH-11](#ph-11-적수-산출--일수-방식--베이스라인) | 적수 산출 · 일수 방식 · 베이스라인 | S1 | 2.5 | PH-60 (PH-40은 나오면 교체) | 베이스라인 10/6 | — | 예정 |
| [FE #139](#fe-139-matured-정책표) | MATURED 정책표 | S1 | 0.5 | PH-10 | — | FE #139 | 예정 |
| [FE #101](#fe-101-계좌비밀번호-토큰-하드코딩-제거) | 계좌비밀번호 토큰 하드코딩 제거 (1차 잔여) | S1 | 0.5 | — | — | FE #101 | 예정 |
| [PH-12](#ph-12-이자-계산기--절사-규칙) | 이자 계산기 · 절사 규칙 | S2 | 3.5 | PH-11 | **10/16** | — | 예정 |
| [PH-90](#ph-90-원장잔액--출금가능액-분리--transferprecheck) | 원장잔액·출금가능액 분리 + `TransferPreCheck` | S2 | 3 | PH-87 · PH-99 | **10/16** (P4 머지) | — | 예정 |
| [PH-70-①](#ph-70-rds-①-modulesdata-plan-s2--②-복원컷오버-s3) | `modules/data` Terraform plan | S2 | 1 | PH-50 | — | — | 예정 |
| [PH-15](#ph-15-만기--감지--matured-전이--원리금--만기해지-api) | 만기 — 감지 · MATURED · 원리금 · 만기해지 API | S3 | 3.5 | PH-12 · PH-10 · PH-43 · 입금 계약(PH-99) | **10/23** | #419 | 예정 |
| [PH-14](#ph-14-이자-지급--원천징수) | 이자 지급 · 원천징수 | S3 | 1.5 | PH-12 · PH-24 · 입금 계약(PH-99) | — | — | 예정 |
| [PH-13](#ph-13-적수-집계-sql-전환--재측정) | 적수 집계 SQL 전환 · 재측정 | S3 | 1.5 | PH-11 | — | — | 예정 |
| [PH-16](#ph-16-중도해지--휴면-전환--해지-시-자원-정리) | 중도해지 · 휴면 전환 · 해지 시 자원 정리 | S3 | 4 | PH-15 · PH-43 · 입금 계약(PH-99) · #467 | 휴면 스텝 통합 **10/30** | — | 예정 |
| [PH-18](#ph-18-개인정보-컬럼-암호화) | 개인정보 컬럼 암호화 | S3 | 2 | — | — | — | 예정 |
| [PH-70-②](#ph-70-rds-①-modulesdata-plan-s2--②-복원컷오버-s3) | RDS 복원 · 컷오버 (DB 쪽) | S3 | 1.5 | PH-70-① · PH-51 | **10/26 apply · 10/27 전환** | — | 예정 |
| [PH-58](#ph-58-엣지-계층) | 엣지 — WAF · Route53 · ACM · CloudFront | S3 | 1.5 | ACM: 가비아 DNS 권한 · WAF 연결: PH-51 | **ACM ARN → P5 10/16 (S2 말)** | — | 예정 |
| [PH-72](#ph-72-접근통제-인프라) | 접근통제 — IAM · Secrets Manager · CloudTrail · Object Lock 버킷 | S3 | 2.5 | 버킷: 없음 · 나머지: PH-51 | **Object Lock 버킷 10/21** | — | 예정 |
| [FE C-1](#fe-c-1-잔액출금가능액-라벨-분리) | 잔액/출금가능액 라벨 분리 (+#139 실연동) | S3 | 1 | PH-90 · FE C-6 | 10/21~ | — | 예정 |
| [FE B-3](#fe-b-3-만기-안내) | 만기 안내 | S3 | 0.5 | PH-15 | — | — | 예정 |
| [PH-19b](#ph-19b-생명주기-최종-검증--리포트) | 생명주기 최종 검증 · 15만 계좌 전수 대조 · 리포트 | S4 | 2.5 | 전 태스크 | 11/6 | — | 예정 |
| [PH-54](#ph-54-dr-등급--복구-훈련) | DR 등급 · 페일오버 · 복구 훈련 | S4 | 3 | PH-51 · PH-70-② | — | — | 예정 |

### P3 — 회계 GL · 정보계 설계 · 기획 리드 · 관리자 화면

| ID | 제목 | S | 인일 | 선행 | 기한 | 이슈·PR | 상태 |
|---|---|---|---|---|---|---|---|
| [PH-97](#ph-97-관리자-고객-계정-운영-api) | 관리자 고객 계정 운영 API (S0 이월분) | S1 | 0.5 | — | — | #449 · PR #459 · #450 | 진행 |
| [PH-87](#ph-87-핵심-용어) | 핵심 용어 정의 | S1 | 0.5 | — | 9/23 | #447 · PR #453 | 완료 |
| [PH-20](#ph-20-계정과목-체계--gl-테이블) | 계정과목 체계 · GL 테이블 | S1 | 2.5 | — | **10/1 Flyway** | #451 · PR #478 | 진행 |
| [PH-21](#ph-21-전표-기표-구조--개시-잔액--분개-패턴표) | 전표 기표 구조 · 개시 잔액 · 분개 패턴표 | S1 | 2.5 | PH-20 | **패턴표 9/30 → P6** | #452 · PR #491 | 진행 |
| [#359](#359-도메인-간-계약면-결정) | 도메인 간 계약면 결정 ADR | S1 | 0.5 | — | — | #359 | 예정 |
| [#475](#475-상품가입-계좌비밀번호-토큰-검증-연결) | 상품가입 계좌비밀번호 토큰 검증 연결 | S1 | 0.5 | — | — | #475 | 예정 |
| PM | 기획 리드 (주 0.5일 상한) | S1 | 0.5 | — | — | — | — |
| [PH-24](#ph-24-gl-분개-서비스--이체-기표-훅--glapi) | GL 분개 서비스 · 이체 기표 훅 · `gl.api` | S2 | 4.5 | PH-21 · PH-99 | 시그니처 9/30 · PR 10/12 · **머지 10/14** | — | 예정 |
| [PH-28](#ph-28-시산표-api--베이스라인) | 시산표 API · 베이스라인 | S2 | 2.5 | 베이스라인: PH-60b · API: PH-24 | 베이스라인 10/8 · API 10/16 | — | 예정 |
| [PH-75](#ph-75-정보계-마트-설계) | 정보계 마트 설계 | S2 | 1 | PH-40 | **10/16** → P5 | — | 예정 |
| PM | 기획 리드 | S2 | 0.5 | — | — | — | — |
| [GL-IDX](#gl-idx-시산표-인덱스-개선) | 시산표 인덱스 개선 · 재측정 | S3 | 1 | PH-28 | — | — | 예정 |
| [PH-28b](#ph-28b-회계-결함-주입--탐지율) | 회계 결함 주입 · 탐지율 | S3 | 2.5 | PH-28 · PH-38 | S3 2주차 | — | 예정 |
| [FE ADM-02](#fe-adm-02-시산표-128) | 시산표 화면 실연동 (#128) | S3 | 0.5 | PH-28 | 10/19~20 | FE #158 | 예정 |
| [FE C-6](#fe-c-6-화면-라벨--용어-대조) | 화면 라벨 ↔ 용어 대조 | S3 | 0.5 | PH-87 | **10/19~20** (C-1보다 먼저) | — | 예정 |
| [FE C-2](#fe-c-2-영업일거래일-표기) | 영업일·거래일 표기 | S3 | 0.25 | PH-40 | — | — | 예정 |
| [FE ADM-04](#fe-adm-04-대사-불일치-목록-130-①) | 대사 불일치 목록 (#130-①) | S3 | 0.5 | PH-38 목록 API | — | FE #130 | 예정 |
| [FE B-8](#fe-b-8-타행이체-입력결과) | 타행이체 입력·결과 | S3 | 1 | PH-33-② | 10/26~29 | — | 예정 |
| [FE 조회 사유](#fe-조회-사유-입력) | 관리자 조회 사유 입력 (#127 · ADM-01) | S3 | 0.5 | PH-89 | — | — | 예정 |
| PM | 기획 리드 | S3 | 1 | — | — | — | — |
| [PH-09](#ph-09-uat-시나리오-실행) | UAT 시나리오 실행 | S4 | 1 | 10/30 배포본 | — | — | 예정 |
| [PH-48](#ph-48-발표-자료) | 발표 자료 · 통합 | S4 | 1 | 6인 리포트 | 자료 11/9 주 · **발표 11/18**([D-24](README.md#d-24--923--리드)) | — | 예정 |
| P3-RPT | 회계 통제 + 정보계 리포트 | S4 | 1 | — | 11/6 | — | 예정 |
| [FE ADM-03](#fe-adm-03-정보계-대시보드) | 정보계 대시보드 (#131-정보계) | S4 | 0.5 | PH-77 | — | FE #146 | 예정 |
| FE C-10 | UAT FE 참여 | S4 | 0.5 | PH-09 | — | — | 예정 |
| [FE B-1①](#fe-b-1①-상품-rag-챗) | 상품 RAG 챗 — 질의·답변·면책 | S4 | 1 | PH-65-② | PH-65가 10/28 전 머지되면 S3 | — | 대체([D-22](README.md#d-22--923--팀-합의멘토-제출-기획서)) |
| PM | 기획 리드 | S4 | 0.5 | — | — | — | — |
| AI-WIKI | 개발자 협업 RAG — 1단계 서빙 배포 완료, 남은 일: PR #466 머지 · 팀원 SSM 권한 | S1 | 0 (인일 밖, [D-23](README.md#d-23--923--리드)) | — | — | PR #466 | 진행 |

### P4 — 이체 코어 · 확장점 · 타행 이체 · 대사 · 정합성 리포트

| ID | 제목 | S | 인일 | 선행 | 기한 | 이슈·PR | 상태 |
|---|---|---|---|---|---|---|---|
| [PH-99](#ph-99-이체-파이프라인-확장점seam) | 이체 파이프라인 확장점(seam) | S1 | 1.5 | — | **10/2** (목표 9/30) | — | 예정 |
| [PH-31](#ph-31-고정길이-전문-규격) | 고정길이 전문 규격 (문서) | S1 | 0.5 | — | — | — | 예정 |
| [PH-38](#ph-38-원장-대사-배치) | 원장 대사 배치 — 착수 | S1 | 1 | — | — | #378 · PR #463 | 완료 |
| [PH-33-①](#ph-33-대외계-①-모의-서버-s1--②-타행-이체-s3) | 모의 대외계 서버 · 전문 어댑터 | S1 | 1.5 | PH-31 | — | — | 예정 |
| [#379 규격](#379-규격-전달) | 파티션 프로시저 규격·판단 → P5 | S1 | 0 (완충) | — | **10/6** | #379 | 예정 |
| [PH-80](#ph-80-timeout--정정-체인--자동-재시도-금지) | 정정 체인 · INVALID · 재시도 금지 규약 (TIMEOUT 제외분) | S1 | 1.5 | PH-99 | — | — | 예정 |
| [PH-38](#ph-38-원장-대사-배치) | 대사 — 불일치 저장 · 목록 API · 300만 베이스라인 · CobStep 편입 | S2 | 2 | PH-60b · CobStep 인터페이스 | 베이스라인 10/6 · API 10/16 | #468 | 예정 |
| [PH-80](#ph-80-timeout--정정-체인--자동-재시도-금지) | TIMEOUT 추가 · 전체 머지 | S2 | 2.5 | PH-80 S1분 · P5 회차 현황 (10/6) | **10/8 머지** | — | 예정 |
| [PH-99](#ph-99-이체-파이프라인-확장점seam) | `transfer.api` 입금 계약 (P2 이자·만기·해지용) | S2 | 0.5 | PH-99 | **10/16** | — | 예정 |
| [EVT-2](#evt-2-publishevent-두-줄) | `publishEvent` 두 줄 | S2 | 0.5 | PH-32 | 10/6 | — | 예정 |
| [HOOK-MERGE](#hook-merge-훅-구현체-머지) | 훅 구현체 3건 리뷰·머지 | S2 | 0.5 | PH-90 · PH-24 · PH-41 | 10/12~16 | — | 예정 |
| [PH-33-②](#ph-33-대외계-①-모의-서버-s1--②-타행-이체-s3) | 타행 송신 · 조회거래 · 미결제 기표 · 격리 | S3 | 5 | PH-33-① · PH-24 | **10/23 머지** | — | 예정 |
| [PH-36](#ph-36-장애-주입-3종--보상-트랜잭션) | 장애 주입 3종 · 보상 트랜잭션 | S3 | 2 | PH-33-② · PH-80 | — | — | 예정 |
| [LOCK-TUNE](#lock-tune-락-튜닝) | 락 튜닝 | S3 | 1 | PH-30 베이스라인 · PH-80 | — | — | 예정 |
| [PH-36b](#ph-36b-최종-측정--리포트) | 최종 측정 · 정합성+성능 리포트 | S4 | 3 | PH-30 재측정 | 11/6 | — | 예정 |

### P5 — COB · 영업일 · 네트워크/컴퓨트 · 정보계 ETL·조회 API · 운영

| ID | 제목 | S | 인일 | 선행 | 기한 | 이슈·PR | 상태 |
|---|---|---|---|---|---|---|---|
| [PH-40](#ph-40-영업일-도입--ph-41-거래일-컬럼) | 영업일 provider · 공휴일 시드 · 거래일 컬럼 · ArchUnit | S1 | 3 | — | **10/2** | #471 #472 #473 | 예정 |
| [PH-50](#ph-50-네트워크-terraform) | 네트워크 Terraform · 원격 상태 · 출력값 | S1 | 3.5 | — | **10/2** plan · 출력값 → P2 | #474 | 예정 |
| [PH-85](#ph-85-adr--미도입-결정) | ADR — Spring Batch·MQ·API GW 미도입 | S1 | 0.5 | — | — | — | 예정 |
| [PH-94](#ph-94-expand-contract-규약) | Expand-Contract 규약 1절 | S1 | 0.5 | — | — | — | 예정 |
| [PH-43](#ph-43-cob-파이프라인) | COB 러너 · `CobStep` · 실행 기록 · 대사 정책 · 베이스라인 | S2 | 4.5 | PH-40 (베이스라인: PH-60b) | 인터페이스 **10/6** · 베이스라인 10/16 | #371 | 예정 |
| [PH-42](#ph-42-correlation-id--로그-마스킹--json-로깅) | correlation id · 로그 마스킹 · JSON 로깅 | S2 | 2.5 | — | **10/8** | — | 예정 |
| [PH-57](#ph-57-용량-산정) | 용량 산정 표 | S2 | 0.5 | PH-60b | — | — | 예정 |
| [PH-47](#ph-47-메트릭--grafana) | 메트릭 · Grafana · 알람 | S2 | 3 | PH-42 | — | — | 예정 |
| [PH-51](#ph-51-컴퓨트-계층--컷오버) | 컴퓨트 계층 · 컷오버 | S3 | 4.5 | PH-50 · PH-57 · PH-58 ACM ARN | 리허설 10/19~23 · **apply 10/26 · 컷오버 10/27** | — | 예정 |
| [PH-76](#ph-76-정보계-etl) | 정보계 ETL | S3 | 2.5 | PH-75 · PH-43 | — | — | 예정 |
| [PH-77](#ph-77-정보계-조회-api) | 정보계 조회 API | S3 | 1.5 | PH-76 | **10/28** | — | 예정 |
| [PH-45](#ph-45-cob-병렬화--365) | 청크 → 병렬 스텝 · 실패 격리 · #365 | S3 | 3 | PH-43 | — | #365 | 예정 |
| [PH-56](#ph-56-was-스케일아웃-비선형성-실증) | WAS 스케일아웃 비선형성 실증 | S4 | 1.5 | PH-51 · PH-30 · PH-49c | — | — | 예정 |
| [PH-55](#ph-55-배포-파이프라인--destroy-리허설) | 배포 파이프라인 · 환경 3단계 · 권한 분리 · destroy 리허설 | S4 | 4 | PH-51 | — | — | 예정 |
| P5-RPT | 배치 + 인프라 리포트 · 런북 | S4 | 1.5 | — | 11/6 | — | 예정 |

### P6 — 목데이터 · 서비스 안내 내비게이션 · 세션 외부화 · MFA 정의

약관 RAG 태스크 8건은 [D-22](README.md#d-22--923--팀-합의멘토-제출-기획서)로 내비게이션이 대신한다. 인일 18은 NAV로 넘어가고, 태스크 분해는 [O-07](README.md#8-미결)(9/30)에서 한다. §2의 RAG 상세 절은 분해 전까지 참고로 남긴다.

| ID | 제목 | S | 인일 | 선행 | 기한 | 이슈·PR | 상태 |
|---|---|---|---|---|---|---|---|
| [PH-60](#ph-60--ph-60b-목데이터) | 목데이터 1단계 — 최소 시드 | S1 | 3 | — | **9/30** | — | 예정 |
| [PH-60b](#ph-60--ph-60b-목데이터) | 목데이터 2단계 — 300만 원장 + 600만 분개 | S1 | 3 | PH-60 · PH-10 · PH-20 · PH-21 | **10/2** (한계 10/6) | — | 예정 |
| [PH-22'](#ph-22-rag-문서-선정) | RAG 문서 선정 | S1 | 1 | — | — | — | 대체([D-22](README.md#d-22--923--팀-합의멘토-제출-기획서)) |
| [PH-61](#ph-61-조항-파서--색인) | 조항 파서 · OpenSearch 색인 · 벡터 베이스라인 | S2 | 3.5 | PH-22' | 베이스라인 10/16 | — | 대체([D-22](README.md#d-22--923--팀-합의멘토-제출-기획서)) |
| [PH-29'](#ph-29-평가셋--응대-정책--프롬프트-v1) | 평가셋 80 · 응대 정책 · 프롬프트 v1 | S2 | 2.5 | PH-22' | — | — | 대체([D-22](README.md#d-22--923--팀-합의멘토-제출-기획서)) |
| [PH-49c](#ph-49c-세션-외부화) | 세션 외부화 — Spring Session Redis | S2 | 1 | PH-49a-② | **10/16** | — | 예정 |
| [PH-92](#ph-92-mfa--step-up-매트릭스) | MFA · Step-up 매트릭스 | S2 | 0.5 | — | 10/16 전 | — | 예정 |
| [PH-65-①](#ph-65-rag-서빙-①-스키마-1016--②-구현-1028) | RAG 서빙 API 스키마 | S2 | 0.5 | — | **10/16** | — | 대체([D-22](README.md#d-22--923--팀-합의멘토-제출-기획서)) |
| [PH-62](#ph-62-하이브리드-검색) | 하이브리드 검색 | S3 | 2 | PH-61 | — | — | 대체([D-22](README.md#d-22--923--팀-합의멘토-제출-기획서)) |
| [PH-63](#ph-63-llm--근거-검증--평가-자동화) | LLM 연결 · 근거 검증 · 평가 자동화 | S3 | 2.5 | PH-62 · PH-29' | v1 수치 10/23 | — | 대체([D-22](README.md#d-22--923--팀-합의멘토-제출-기획서)) |
| [PH-65-②](#ph-65-rag-서빙-①-스키마-1016--②-구현-1028) | 서빙 API · Spring 어댑터 · degrade | S3 | 2 | PH-63 | **10/28** | — | 대체([D-22](README.md#d-22--923--팀-합의멘토-제출-기획서)) |
| [PH-73](#ph-73-가드레일) | 가드레일 | S3 | 1.5 | PH-63 | — | — | 대체([D-22](README.md#d-22--923--팀-합의멘토-제출-기획서)) |
| [PH-66](#ph-66-최종-평가) | 최종 평가 · RAG 품질 리포트 | S4 | 2.5 | — | 11/6 | — | 대체([D-22](README.md#d-22--923--팀-합의멘토-제출-기획서)) |
| NAV | 서비스 안내 내비게이션 — 화면 이동·입력 안내·허용 목록·AI 품질 리포트 | S1~S4 | 18 (위 대체분) | — | 분해 9/30([O-07](README.md#8-미결)) · 리포트 11/6 | — | 예정 |

---

## 2. 태스크 상세

같은 태스크의 스프린트 분할(`-①`/`-②`, S1/S4)은 한 절에 모았다.

## 2-1. P1 태스크 상세

### PH-32. 도메인 이벤트 발행 골격

- 이벤트 클래스 — `TransferCompleted(transactionNumber, sourceType, amount, fromAccountId, toAccountId, occurredAt)` · `TransferFailed(...)` · `SubscriptionOpened(...)` · `AccountMatured(...)`. `AccountMatured`는 타입만 정의하고 2차에는 소비하지 않는다. 이름은 과거형이고, Reference Key는 거래번호다.
- `@TransactionalEventListener(phase = BEFORE_COMMIT)` 리스너 자리와 **JdbcTemplate INSERT 규약**을 문서로 남긴다. 규칙은 [README §3-3](README.md#3-3-이벤트아웃박스)을 따른다.
- `publishEvent` 자리와 페이로드는 P4와 합의한다. 자리 표시는 PH-99(10/2)가 하고, 두 줄은 EVT-2(10/6)가 넣는다.
- 테스트: BEFORE_COMMIT 리스너 INSERT가 커밋에 실리는지 통합 테스트(PH-99 골격 위), 리스너 예외 시 롤백을 테스트로 고정.
- **완료 기준:** 롤백되면 이벤트가 발행되지 않는다. 리스너 INSERT가 커밋된다.
- 현황: `ApplicationEventPublisher`·`@TransactionalEventListener` 사용처 0건.

### PH-30. k6 harness · 이체 TPS · 동시성 정합성 (S1 베이스라인 · S4 재측정)

9/18 결정으로 PH-30 전체가 P1 소유다([D-03](README.md#d-03--918--p1p4-합의)). P4는 수치를 받아 리포트에 싣는다.

- ① k6 시나리오 3종 — 분산(계좌 분산) · **핫스팟(동일 계좌 동시 100건)** · 동일 계좌 100/500/1000. **`BASE_URL`과 VU를 파라미터로** 받는다. P5 PH-56이 3-Tier ALB DNS에 같은 harness를 돌리기 때문이다.
- ② 이체 TPS·p95·p99 베이스라인(단일 EC2). ②-b 한도 경로(`checkAndReserve`)의 p95·p99를 따로 잰다.
- ③ 데드락률 · 잔액 오차 · 원장 짝 무결성 (#384)
- ④ 리포트 양식(평균·p95·**p99** 칸)과 `harness.md`를 공유한다. 모든 트랙의 성능 리포트가 이 양식을 쓴다.
- 요청받아 추가로 재는 것: P3 PH-24 훅 전/후 이체 성능 저하율, P4 HOOK-MERGE 저하율, P4 LOCK-TUNE 전/후. 이 재실행은 S2~S3에 일어나지만 인일은 S1 4일과 S4 0.5일에 포함한다.
- S4: 같은 harness·같은 구간으로 재실행한다. 잔액 오차 N → 0. 잔액 기준은 P2 PH-90과 맞춘다.
- **기한:** harness 10/2(PH-56·LOCK-TUNE이 기다린다), 개선 전 수치 10/8.
- 현황: 레포에 k6 없음.

### PH-01. 알림 도메인 인수 · 영속성

- P6에게서 1시간 인수받는다. `notification` 테이블은 `V202608010900`에 있고 자바 코드는 0건이다.
- 도메인 모델 · JPA 엔티티 · Mapper · 저장 포트 (AGENTS.md 규칙 1)
- 알림 유형 4종은 DDL 주석 그대로다(TRANSFER · SCHEDULED_TRANSFER · AUTO_TRANSFER · PRODUCT_SUBSCRIPTION). 이벤트 `sourceType`과의 매핑표를 만든다. **기존 마이그레이션은 수정하지 않는다.**
- 알림 API 계약 JSON(목록·읽음·미읽음)을 `docs/phase2/`에 둔다. 같은 S3에 실 API가 나오므로 FE 목은 갱신하지 않는다.
- **완료 기준:** 저장·조회 단위 테스트가 있다.

### PH-49a. 관리자 인증 (① 결정·스키마 10/2 → ② 구현 10/8)

**① 결정 5개를 `docs/phase2/admin_auth.md`에 적는다.**

1. 저장 모델: `customer.role`(CUSTOMER/ADMIN)과 `customer.permissions` VARCHAR(200) CSV
2. 권한은 고정 5종: `GL_READ · GL_WRITE · CUSTOMER_READ · CUSTOMER_WRITE · AUDIT_READ`. 부여·회수 API는 PH-49b에서 만들고, 이 시점에는 시드로만 준다.
3. 로그인 경로를 `/admin/auth/login`으로 분리
4. 관리자 세션 30분 무조작 만료(고객은 10분)
5. `GET /customers/me`에 `role`·`permissions[]` 추가

①의 나머지:

- Flyway로 `role`·`permissions` 컬럼을 추가한다. P3 `customer.status`(#450)와 **다른 파일**로 만든다.
- 시드: local·test에 2명(조회 전용·변경 가능), prod는 SQL Runbook으로 1명
- 로그인 계약 JSON과 `/customers/me` 예시를 FE #136에 넘긴다.

**②**

- `/admin/auth/login`: 기존 `SessionLoginManager`를 재사용하고, role=ADMIN만 통과시킨다. 실패 응답은 고객 로그인과 같은 문구로 한다(AGENTS 규칙 4).
- `/admin/**` 필터: ADMIN이 아니면 403. 변경 메서드는 `*_WRITE`를 검사한다. URL 접두어 → 권한 매핑표 1장을 만든다.
- **S0 임시 가드를 제거한다** — `AdminBootstrapAuthorizationManager`·`AdminBootstrapProperties`·`app.security.admin.bootstrap-customer-ids`. 제거 체크리스트는 #448 코멘트에 있다.
- 관리자 로그인·로그아웃 감사로그: `AuditEventType.LOGIN`에 actor role을 포함한다.
- 권한 매핑: 정보계(ADM-03·PH-77)는 `GL_READ`([D-09](README.md#d-09--923--리드)), 대사 불일치(ADM-04·PH-38 목록)는 `AUDIT_READ`
- **완료 기준:** 조회 전용 ADMIN이 PH-97 변경 API를 호출하면 403이 난다. FE 관리자 채널이 실 응답으로 돈다.
- 현황: `customer`에 role·permissions 컬럼 없음. 코드의 `/admin/**`은 임시 가드(#448, PR #455 머지)만 있다.

### PH-03. API 문서 전수 점검 (① 측정 S1 → ② 0 마감 S4)

- ①: 엔드포인트 전수 목록화(AI)와 미문서화 건수 기록. PR #476(9/23 머지)이 52개 오퍼레이션을 점검했다. **개선 전 = 설명이 빈 오퍼레이션 9개, 보강 후 0개.** S4는 2차에 새로 생긴 API를 포함해 다시 잰다.
- ②: S1 측정 결과를 Swagger 수정으로 0까지 줄인다. 전 도메인 PR은 P1이 내고 도메인 담당이 리뷰한다. S4에 허용되는 문서 변경이다.

### FE-SNAP. codegen 스냅샷

- FE 레포에 `openapi.snapshot.json`을 커밋하고, `ci.yml`의 `pnpm codegen`이 스냅샷을 읽게 한다. `OPENAPI_SPEC_URL`은 로컬 개발용으로 유지한다.
- `OPERATION_ID` 표에 관리자 엔드포인트 규칙을 추가한다. 서버 릴리스가 FE CI를 깨는 구조(FE #142)를 끊는 것이 목적이다.
- **FE 레포 규약도 함께 바꾼다.** 지금 FE의 codegen 규약은 `orval.config.ts` 주석과 `openapi-transformer.ts`(`OPERATION_ID`)에만 있고, 추적되는 문서가 없다(`.claude/`·`CLAUDE.md`는 FE `.gitignore` 대상이라 팀원이 볼 수 없다). FE에 `docs/api-codegen.md`를 새로 두고 스냅샷 갱신 절차(누가, 언제, 어떤 PR로)를 적는다.
- `pnpm codegen`을 부르는 워크플로는 `ci.yml`과 `e2e.yml` **둘 다** 스냅샷으로 바꾼다.
- **완료 기준:** 서버 dev가 바뀌어도 FE CI가 스냅샷으로 통과하고, 스냅샷 갱신 PR 절차가 FE 규약 문서에 있다.
- 현황: FE 레포에 스냅샷 파일 없음.

### PH-96. Transactional Outbox (① 발행측·릴레이 S2 → ② 구독 S3)

**①**

- `outbox` 테이블(Flyway + `schema_reference.md` + ERD): id · event_type · reference_key · payload(JSON) · status(NEW/SENT/FAILED) · retry_count · created_at
- BEFORE_COMMIT 리스너가 **JdbcTemplate INSERT**로 같은 커밋에 적재한다.
- 릴레이: 기존 스케줄러가 5초 주기로 폴링한다. At-Least-Once이고, 3회 실패하면 FAILED(DLQ 역할)다. `BatchExecutionLockPort`로 단일 인스턴스에서만 실행한다(S3 말부터 WAS 2대).
- `notification.api.OutboxEventHandler`와 멱등 키(reference_key + event_type 유니크)
- 개선 전 수치(10/8): AFTER_COMMIT을 흉내 내 리스너에서 강제 종료시키고 유실을 1회 재현해 기록한다.

**②**

- 즉시이체 #393 · 예약이체 #394 · 자동이체 회차 #395는 같은 `transfer.completed`를 `sourceType`으로 분기한다. 상품가입은 #396이다.
- 구독 멱등(두 번째 수신은 no-op). 알림 실패는 릴레이 재시도로만 처리되고 이체에 영향을 주지 않는다.

**완료 기준:** 발행 직후 프로세스를 죽여도 재기동 후 전달된다. 4종 이벤트마다 알림 1건이 생긴다. 중복 발행해도 1건이다.

### PH-89. 감사 5W · 조회 감사 · 조회 사유 강제

- 5W 필드 표준화(Who · When · Where · What · **Why**)
- **Where 정정 (#458)** — prod 감사 IP를 클라이언트가 조작할 수 있는 `X-Forwarded-For` 첫 값이 아니라, 신뢰 프록시(ALB) 기준 값으로 기록한다.
- `AuditEventType`에 조회 이벤트를 추가한다(예: 고객 조회).

**조회 사유 규약** ([D-10](README.md#d-10--923--리드))

| 항목 | 규약 |
|---|---|
| 대상 | 고객 개인정보를 여는 관리자 API(고객 검색·상세)와 고객 변경 API(잠금해제·비밀번호 초기화·상태변경). 시산표·대사·정보계는 개인정보가 없어 제외 |
| 전달 | 요청 헤더 `X-Access-Reason: <코드>`. GET·POST에 같은 방식을 쓴다 |
| 코드 | `CUSTOMER_REQUEST`(고객 요청) · `INCIDENT`(사고·민원 대응) · `AUDIT`(감사·점검) · `OPERATION`(운영 확인). 자유기재는 2차 범위 밖 |
| 누락·미정의 코드 | 400 + 신규 오류코드(`api_conventions.md` §4에 등록) |
| 기록 | 조회 감사 이벤트의 Why에 코드를 적는다 |

**범위 밖:** NTP 동기화, JSON 정규화, 감사 조회 API(소비 화면 FE #130 감사로그 탭이 범위 밖).
**완료 기준:** 사유 헤더 없이 고객 검색을 호출하면 400이 난다. 조회 1건마다 5W가 채워진 감사 행 1건이 남는다.
현황: `AuditEventType` 13종에 조회 이벤트가 없다.

### FE #136. 관리자 세션 정책 · 로그인 훅 교체

PH-49a-① 문서로 정책을 대신한다. 별도 화면은 없다. FE 로그인 훅을 `/admin/auth/login`으로 바꾸고 30분 만료를 반영한다(FE `requirements-admin.md` REQ-ADM-006·007).
**완료 기준:** 관리자 로그인이 `/admin/auth/login`을 호출하고, 관리자 세션이 30분 무조작 후 만료돼 `/admin/login`으로 이동한다.

### PH-04. 알림 조회 API · 마스킹 통일

- 목록(#390, 커서 페이지네이션) · 읽음(#391) · 미읽음 건수(#392)
- 마스킹 호출부를 공용 `common/util/MaskingUtil`로 합친다(#410). 현재 `customer/.../CustomerInfoMasker`가 따로 있다. 응답 DTO를 전수 점검해 "마스킹 미적용 필드" 수치를 낸다.
- 알림 20건 조회 쿼리 1~2개(fetch join)
- **완료 기준:** 마스킹 미적용 0. 미읽음 조회 p95·p99 기록.

### PH-06. 지연이체 서비스

전자금융감독규정상 필수 제공 서비스다. 입금계좌 지정(PH-07)은 2차에 없다([D-06](README.md#d-06--918--리드)).

- 고객별 설정(on/off · 기준금액 · 지연시간)
- 지연 대기 이체를 예약으로 처리한다(P5 예약이체 배치 재사용 검토)
    - **즉시이체를 보류로 돌리는 자리가 PH-99 seam에 없다.** 훅 A는 읽기 전용이라 보류로 바꿀 수 없다. 컨트롤러 단에서 예약이체로 위임할지, P4와 새 훅을 합의할지는 [O-09](README.md#8-미결)에서 정한다. `execute()`를 직접 고치지 않는다.
- 지연 중 취소
- 대상 판정과 안내(남은 취소 가능 시간을 응답에)
- **완료 기준:** 기준금액 이상 이체가 지정 시간 후 입금된다. 취소가 동작한다.
- **범위 밖:** FE B-6(지연이체 화면)

### PH-08. 레이트리밋

#332 대응이다. 반복 요청으로 계정 존재 여부를 구분할 수 있다는 문제다.

- Redis 카운터로 로그인·OTP·이체를 제한한다.
- **IP+아이디 조합** 실패 카운터로 계정 열거를 차단한다. 존재하지 않는 아이디도 실패 횟수를 누적하고, 같은 `ATH0102`로 전환한다.
- `429` 응답 규약을 문서화한다. TTL은 자체 정책값(예: 15분)으로 정한다.
- 무차별 로그인 1만 건 주입 테스트
- **IP 단위 전역 요청량 제한은 P2 PH-58 WAF가 맡는다**([D-07](README.md#d-07--919--p1p2)).
- **완료 기준:** 차단률 · 오탐률 · 응답 오버헤드 기록
- **범위 밖:** FE C-4(오류 문구)

### PH-49b. 직무분리 부여/회수 · maker-checker · Step-up 표기

- `permissions[]`에 `*_READ`/`*_WRITE`를 부여·회수하는 API. 변경 API는 전부 기존 `AuditLog`를 거친다.
- P6 PH-92 매트릭스를 받아 관리자 변경 API에 Step-up 요구 구간을 표기한다.
- maker-checker는 역분개·한도변경을 승인 대기로 두는 데까지 **검토만** 한다. 전면 도입은 범위 밖이다.
- **완료 기준:** 조회 전용 관리자가 변경 API를 부르면 거부된다. 부여·회수가 감사로그에 남는다.
- **범위 밖:** FE 권한 관리 화면(FE #129는 닫힘)

### PH-82. WORM 감사 저장소 · 무결성 해시

- S3 Object Lock 버킷에 적재한다. 버킷과 IAM은 P2 PH-72가 **10/21까지** 만든다. 그 전에는 해시 컬럼·검증 배치·적재 어댑터(목 버킷)를 먼저 만든다. **GOVERNANCE 모드 · 보존 1일**이다([D-08](README.md#d-08--91920--p1p2p5)).
- 적재 대상: 기존 `AuditLog`와 개인정보 접근로그(PH-89 조회 감사)
- 거래 레코드에 SHA-256 해시 컬럼을 두고, 검증 배치가 불일치 리포트를 낸다.
- **삭제·수정 거부 검증 시나리오는 P1이 한다.**
- **완료 기준:** 보존기간 안에 객체 삭제를 시도하면 거부된다.

### FE B-9. TIMEOUT 화면 — 재시도 차단

즉시이체 화면(P1 소유). PH-80이 10/8에 넘기는 TIMEOUT 응답 스키마를 쓴다. TIMEOUT이면 재시도 버튼을 막는다. **B-8과 같은 디렉터리라 파일 경계는 P1이 정하고 P3에 알린다.**
**완료 기준:** 이체 결과가 TIMEOUT이면 "처리 결과 확인 중" 안내가 뜨고 재시도·재이체 버튼이 없다. SUCCESS·ERROR 화면은 그대로다.

### FE C-7. 알림함 실연동

목을 제거하고 미읽음 배지를 붙인다.
**완료 기준:** 알림함이 MSW 목 없이 PH-04 실 API로 목록·읽음을 처리하고, 헤더 배지 숫자가 미읽음 건수 API와 같다.

### FE C-5. correlation id 헤더 전파

`custom-fetch.ts`에서 `X-Correlation-Id`를 전파한다(PH-42 계약).
**완료 기준:** 모든 API 요청에 `X-Correlation-Id`가 실리고, 서버 로그에서 같은 값으로 그 요청을 찾을 수 있다.

### P1-FINAL. 최종 측정 · 품질·기록 리포트

- 1만 건 주입 후 발행 직후 강제 종료 → 유실 0 · 중복 0
- 커버리지 재측정(JaCoCo, 개선 태스크 없음) · 미문서화 API 재측정 · 스냅샷 도입 후 FE CI 파손 0회
- 품질·기록 리포트

### PH-93. SAST · SCA CI 편입 — 조건부

**S4에 여유가 있고 선행인 P5 PH-55가 먼저 끝났을 때만 착수한다.** PH-55가 S4(4인일)에 있어 S4 안에 끝날 가능성은 낮다.

- SAST 정적 분석, SCA 의존성 CVE·라이선스 스캔, 오탐 처리 워크플로
- 개발·배포 권한 분리는 PH-55 몫이다.

## 2-2. P2 태스크 상세

### PH-10. 계좌 상태 전이 매트릭스 + Flyway

- 상태 5종(ACTIVE · SUSPENDED · **MATURED** · CLOSED · DORMANT) × 이벤트 12종 = 60칸
    - 이벤트: 입금 · 출금 · 이체출금 · 이체입금 · 만기도래 · 만기해지 · 중도해지 · 휴면전환 · 휴면해제 · 지급정지 · 지급정지해제 · 해지
- 새 V 파일에서 `ck_account_status`(`V202608040943`이 만든 제약)를 DROP하고 5종으로 다시 만든다. 기존 마이그레이션은 고치지 않는다. **같은 PR에서 `transfer/application/port/out/LockedAccountStatus`(현재 ACTIVE·SUSPENDED·CLOSED 3종)를 동기화**하고, `ddl-auto: validate` 기동을 확인한다.
- 금지 전이 오류코드를 `api_conventions.md` §4에 등록한다. Aggregate Root가 통제하고, 외부에서 직접 UPDATE하지 않는다.
- 전이표를 FE #139에 넘긴다.
- **완료 기준:** 60칸 빈칸 0. Flyway가 9/30 전에 `dev`에 있다(P6 PH-60b가 MATURED 후보를 넣는다).

### PH-11. 적수 산출 · 일수 방식 · 베이스라인

- 일수 방식은 실일수/365로 정하고 [glossary](glossary.md)에 반영한다.
- 금액은 정수 원, 금리는 `BigDecimal` + `RoundingMode` 명시(AGENTS 규칙 5)
- **개선 전 구현은 일부러 계좌별 단건 루프로 짠다.** 베이스라인은 3만 계좌(10/1)와 15만 계좌(10/6)
- 수기 계산 5건과 대조한다. PH-40 전에는 `LocalDate`를 임시로 쓴다.

### FE #139. MATURED 정책표

화면별로 (정상/정지/만기/해지) × (조회 가능/거래 대상) 표를 만든다. 실연동은 C-1과 함께 한다.
**완료 기준:** 표가 FE 문서에 있고 PH-10 전이표와 모순이 없다.

### FE #101. 계좌비밀번호 토큰 하드코딩 제거

1차 잔여다. 서버는 준비돼 있다. 남은 화면 수를 확인하고 교체한다.
**완료 기준:** FE 코드에 하드코딩된 계좌비밀번호 토큰 값이 0건이다.

### PH-12. 이자 계산기 · 절사 규칙

- `product_rate_tier` 기간별 금리를 적용하고 원 단위로 절사한다. 금리는 가입 시점 스냅샷 `applied_rate`만 쓴다.
- 상품 12종 계산 검증(스프레드시트 대조 오차 0)
- 결과 값객체 `InterestResult(gross, tax, net, appliedRate)` — PH-14가 쓰고 P3 분개가 받는다.
- **범위 밖:** 우대금리 조건 판정
- **완료 기준:** 12종 오차 0원

### PH-90. 원장잔액 · 출금가능액 분리 + `TransferPreCheck`

- Flyway로 `account.hold_amount`(지급정지 보류액, BIGINT, 엔티티는 `Long`)를 추가하고 `schema_reference.md`·ERD를 함께 고친다.
- 출금가능액 = 원장잔액 − 보류액. 정의는 [glossary](glossary.md) #2를 따른다. 조회 응답에 `ledgerBalance`·`availableBalance`를 둘 다 넣는다(Expand).
- `AvailableBalancePreCheck implements transfer.api.TransferPreCheck` — 출금가능액이 모자라면 `LMT0001`로 거부한다. **`execute()`는 건드리지 않는다.**
- 출금계좌 **목록** 응답(`AccountOverviewResponse`)에 `availableBalance`·`passwordLocked`를 Expand로 추가한다. FE 출금계좌 선택 화면(C-1)이 이 필드를 기다린다([glossary](glossary.md) §2).
- 현행 `resolveAvailableBalance`의 "비밀번호 잠금이면 0" 판정을 없앤다. `ACTIVE`가 아니면 0이고, 잠금은 `passwordLocked`로 따로 알린다(glossary #2).
- 지급정지 시나리오 테스트(잔액은 그대로, 이체는 거부)
- 화면별로 어느 값을 쓰는지 표를 만들어 C-1에 넘긴다.
- **범위 밖:** 미결제 차감(PH-34)
- 현황: 출금가능액은 계좌 상세에만 있다(`AccountDetailQueryService.resolveAvailableBalance`). 보류액 차감이 없다.

### PH-70. RDS (① `modules/data` plan S2 → ② 복원·컷오버 S3)

**①**

- RDS MySQL 1개(Multi-AZ), 파라미터 그룹(KST, 슬로우쿼리 로그), 자동 백업 7일, 저장 암호화
- 서브넷과 SG는 PH-50 출력값을 참조한다.
- 기존 RDS 스냅샷을 복원하는 방식이다. `terraform import` 절차 1장을 쓰고 `plan`을 통과시킨다.

**②**

- 10/26 오전: 스냅샷 → 새 VPC data 서브넷에 복원 → SG 규칙 검증
- 10/27: Flyway `validate` 확인 후 앱 연결 전환(P5와 함께)
- 적수 배치 1회 시간을 기록한다. 전환 확인용 기록이고 개선 전/후 비교에 쓰지 않는다(README §3-1).
- **Plan B:** 기존 RDS를 유지하고 복원 절차 문서를 리포트에 싣는다.

**범위 밖:** Read Replica · ElastiCache · 커넥션풀 곡선(PH-53)

### PH-15. 만기 — 감지 · MATURED 전이 · 원리금 · 만기해지 API

- 영업일 기준 만기 감지 → `ACTIVE → MATURED`. `batch.api.CobStep` 구현체로 등록하고, 체인 자리는 [PH-43](#ph-43-cob-파이프라인)이 정한다. 스텝은 자기 트랜잭션을 연다.
- `AccountMatured` 이벤트 발행(PH-32 타입, 2차 알림 소비 없음)
- 만기해지 API: 원리금 계산(PH-12) → 출금계좌 입금(PH-99의 `transfer.api` 입금 계약) → CLOSED. `LedgerPair`는 transfer 내부라 직접 쓰지 않는다.
- 계좌 상세 응답에 `maturityDate`·`status=MATURED`를 추가한다(Expand → B-3).
- 만기 처리 단건 비용을 재서 P5 COB 시간에 합산한다.
- **범위 밖:** 만기후이율(MATURED 이후 이자 없음)
- **완료 기준:** 원리금 오차 0. MATURED 계좌가 신규 거래 대상에서 빠진다.

### PH-14. 이자 지급 · 원천징수

- 이자 지급 posting: PH-99의 `transfer.api` 입금 계약으로 계좌에 입금한다(원장 기표 포함).
- 이자소득세 15.4%(소득세 14% + 지방 1.4%, 각각 원 단위 절사). 세율은 yml에 둔다.
- `gl.api.JournalPostingUseCase.post(...)` 호출 2줄 — 차 `50100` 이자비용 / 대 `20100` 예수금, 차 `20100` 예수금 / 대 `20200` 원천세예수금. **이 패턴의 확정은 P2 몫이다.** 확정한 뒤 [gl_journal_patterns.md](gl_journal_patterns.md) §3-4에 적는다(유형 `INT`).
- **범위 밖:** 비과세·세금우대 구분

### PH-13. 적수 집계 SQL 전환 · 재측정

단건 루프를 원장 집계 SQL 1문(+ 인덱스 1건)으로 바꾼다. 15만 계좌를 **베이스라인과 같은 단일 EC2**에서 다시 재고, 결과값이 같은지 검증한다.

### PH-16. 중도해지 · 휴면 전환 · 해지 시 자원 정리

- 중도해지 API, 예치기간 구간별 중도해지이율, 수수료(요율은 yml)
- 휴면 전환 서비스와 휴면 해제 API
    - 휴면 전환은 `CobStep` 구현체로 **만기감지 바로 다음 자리**에 들어간다([PH-43](#ph-43-cob-파이프라인)).
    - **10/30까지 통합하고 S4에는 검증만 한다**([D-12](README.md#d-12--923--리드p2-회신-채택)).
- 계좌 해지 시 연결 자원 정리(자동이체 · 예약이체 · 출금계좌 등록). 자동이체·예약이체는 P5 도메인이라 공개 계약으로만 호출한다(출금계좌 사용 여부는 #467).
- 이자 분개는 PH-14와 같은 `gl.api` 2줄이다.
- **범위 밖:** PH-17 휴면 화면·세부 정책, FE B-4·B-5

### PH-18. 개인정보 컬럼 암호화

`phone_number` 등 연락처 컬럼을 AES로 암복호화(JPA `AttributeConverter`)하고 검색용 해시 컬럼을 둔다. 적용 전/후 조회 성능 저하율을 잰다.

### PH-58. 엣지 계층

- **`corebank.cloud`의 권한 DNS는 가비아다(Route 53 아님).** `api.corebank.cloud`는 지금 기존 ALB `corebank-alb`로 CNAME돼 있다. 먼저 둘 중 하나를 정한다: ① 가비아에 레코드를 계속 두고 ACM 검증 CNAME도 가비아에 넣는다 ② NS를 Route 53으로 위임한다(전파 최대 48시간이라 10/9 전에 시작).
- Route 53(또는 가비아) 레코드·헬스체크, ACM TLS 자동 갱신
- 인증서는 두 개다: ALB용은 ap-northeast-2, CloudFront용은 us-east-1.
- **ACM 인증서는 PH-51을 기다리지 않는다.** 위 DNS 검증만 있으면 발급되므로 먼저 발급해 **certificate ARN을 10/16까지 P5에 넘긴다.** P5의 HTTPS 리스너 리허설(10/19~)이 이 값을 쓴다. WAF 연결만 PH-51 apply(10/26) 뒤다.
- WAF 룰셋(SQLi · XSS · 봇 · **IP 단위 전역 레이트리밋**, [D-07](README.md#d-07--919--p1p2))을 PH-51의 ALB ARN에 연결한다.
- `/admin/*` 경로 단위 IP 제한을 검토한다.
- CloudFront + OAC + S3 정적 호스팅. FE는 Cloudflare Pages를 유지하므로 **병행**이다. 대상이 없으면 "대상 없음"으로 기록한다.
- destroy 제약: [README §3-8](README.md#3-8-3-tier-수명과-destroy)(CloudFront 비활성화 → 삭제 순서)
- **완료 기준:** WAF 차단 로그가 남는다.

### PH-72. 접근통제 인프라

IAM 경계는 [D-11](README.md#d-11--920--p2p5)을 따른다. P5는 인스턴스 프로파일 2개와 SSM Agent·Session Manager 접속 기반까지 맡는다.

- 사람·CI·AI 서비스 IAM 역할 분리. AI 계정은 조회 전용이다.
- Secrets Manager 전환(현재 환경변수)
- CloudTrail · 콘솔 MFA 강제 · 자격증명 회전
- **S3 Object Lock 버킷 + 적재용 IAM — GOVERNANCE 모드 · 보존 1일. 10/21까지 먼저 apply한다.** S3 버킷은 VPC·컴퓨트와 무관하다. 컷오버(10/27) 뒤로 미루면 P1 PH-82가 S3 마지막 3일에 몰리고, S4는 코드 금지라 넘길 곳이 없다. 나머지 IAM·Secrets Manager·CloudTrail은 PH-51 뒤다.
- destroy 대비 설정: Secrets Manager는 `recovery_window_in_days = 0`, CloudTrail·Object Lock 버킷은 `force_destroy = true`로 둔다. 이유와 순서는 [README §3-8](README.md#3-8-3-tier-수명과-destroy)
- **완료 기준:** AI 자격증명으로 쓰기를 시도하면 거부된다. 앱 비밀값이 Secrets Manager에서 읽힌다. Object Lock 객체 삭제가 거부된다(검증은 P1 PH-82). SSH 0은 PH-51 몫이다.

### FE C-1. 잔액/출금가능액 라벨 분리

PH-90 매핑표로 화면별 PR을 낸다. 이체 화면은 P1이 머지하고, 대시보드·계좌 화면은 P2가 머지한다. **C-6(P3) 다음에 한다.**
**완료 기준:** 잔액이 표시되는 모든 화면이 PH-90 매핑표대로 "잔액"과 "출금가능금액" 라벨([glossary](glossary.md) §3)을 쓰고, 지급정지 계좌에서 두 값이 다르게 보인다.

### FE B-3. 만기 안내

계좌 상세에 "만기 D-n / 만기 도달"을 표시한다.
**완료 기준:** 만기일이 있는 계좌 상세에 D-n이 보이고, `status=MATURED`이면 "만기 도달"이 보인다.

### PH-19b. 생명주기 최종 검증 · 리포트

가입 → 이자 → 만기 → 해지 → 중도해지 e2e(P3 PH-09 시나리오 공유), 15만 계좌 원리금 전수 대조(오차 0), 원리금 정확도·잔액 체계 리포트.

### PH-54. DR 등급 · 복구 훈련

S4지만 신규 API가 아니라 문서화·측정·훈련이다.

- 업무별 Tier 1~4
    - Tier1 원장·결산: RTO≤15분, RPO≈0
    - Tier2 온라인: RTO≤1h, RPO≤5분
    - Tier3 알림·관리자: ≤4h
    - Tier4 정보계·AI: ≤24h
- 백업과 DR 구분 문서 1장
- **RDS 강제 페일오버 1회**: 전환 시간과 거래 실패 건수. 이 항목은 P2만 한다.
- 스냅샷 복구 훈련 1회, Failback 절차, SPOF 점검표
- **완료 기준:** 측정한 복구 시간이 Tier별 RTO 안에 드는지 판정한다.

## 2-3. P3 태스크 상세

### PH-97. 관리자 고객 계정 운영 API

- 고객 검색(마스킹), 잠금 해제(기존 `AuditEventType.ACCOUNT_UNLOCK`)
- 로그인 비밀번호 초기화: 임시 비밀번호를 응답으로 준다. `PASSWORD_RESET_BY_ADMIN`을 추가한다.
- 계정 상태 변경: Flyway로 `customer.status`(ACTIVE/SUSPENDED)를 추가한다(#450).
- S0에서 넘어온 몫이다. PH-49a-②가 임시 가드를 걷어내면 이 API들이 `/admin/**` 필터 아래로 들어간다.
- PH-89가 머지되면 사유 헤더 검사를 받는다.
- **범위 밖:** 회원가입 관리(가입 대행·목록)
- **완료 기준:** 잠긴 QA 계정을 관리자 API로 풀고 로그인할 수 있다. prod QA 계정은 10/16 릴리스 이후 PH-49a 인가 아래에서 만든다. 절차 문서 `docs/phase2/qa_accounts.md`는 아직 없다 — P3가 그때 작성한다.

### PH-87. 핵심 용어

완료. [glossary.md](glossary.md)에 11개 용어가 있다. 나머지 라벨 불일치는 FE C-6에서 뽑는다.

### PH-20. 계정과목 체계 · GL 테이블

- 코드 체계(대1·중2·세2), 5분류, 계정 15개 내외 — 예수금 · 미결제타점권 · 이자비용 · 원천세예수금 · 개시잔액(자본) · 현금성 포함
- `gl_account` · `gl_voucher` · `gl_journal_entry` Flyway + `schema_reference.md` + ERD. 금액은 BIGINT다. 정상잔액 방향과 계정 시드를 넣는다.
- **완료 기준:** 시드가 적재되고 P6 생성기가 이 테이블에 쓸 수 있다.

### PH-21. 전표 기표 구조 · 개시 잔액 · 분개 패턴표

- **정본은 [gl_journal_patterns.md](gl_journal_patterns.md)**(PR #491). 아래는 요약이고, 어긋나면 그 문서를 따른다.
- 전표번호: `yyyyMMdd-TTT-NNNNNN`(19자, `voucher_no VARCHAR(20)`). `yyyyMMdd`는 거래일, `TTT`는 `OPN`·`TRF`·`SUB`·`INT`, `NNNNNN`은 (거래일, 유형)마다 1부터 시작하는 **6자리** 일련번호다. 4자리로는 PH-60b 첫날부터 넘친다. PH-40 전에는 `LocalDate`로 임시 채번한다.
- 전표 단위 차대변 일치 검증(도메인 + DB 제약)
- 개시 잔액 전표(`OPN`): 차 `10100` 현금성 / 대 `20100` 예수금(고객 잔액 합계) + 대 `30100` 개시잔액(차액). 0원 줄은 만들지 않는다. P6 적재 순서는 계정 시드 → 개시 전표 → 거래 전표다.
- **당행 이체(`TRF`)와 상품가입 초입금(`SUB`)은 둘 다 차 `20100` 예수금 / 대 `20100` 예수금이다.** 상품가입은 고객의 입출금계좌에서 새 예적금계좌로 옮기는 내부 이동이라 **현금성 계정이 끼지 않는다.** 차대변은 맞아서 검증에 걸리지 않으므로 틀려도 드러나지 않는다. 주의한다.
- 미확정 패턴 2종은 소유 트랙이 정해서 패턴 문서에 추가한다: 이자 지급 2줄(P2 PH-14), 타행 미결제 2패턴(P4 PH-33-②). **PH-60b는 이 둘을 만들지 않는다.**
- **개시·이체·상품가입 패턴은 9/30에 P6에 넘긴다.**
- **완료 기준:** 차대변이 맞지 않는 전표는 예외로 거부된다. 개시 시점 시산표가 맞는다.

### #359. 도메인 간 계약면 결정

`<domain>.api` 단일화 결정 ADR 1건이다. ADR-0003(`batch.api`)을 선례로 쓴다. 결정이 나면 P4 #350 가이드 문서화와 ArchUnit 전 도메인 확대가 따라온다. 모놀리식 ADR은 범위 밖이다.

### #475. 상품가입 계좌비밀번호 토큰 검증 연결

- `subscription/adapter/out/auth/ProductSubscriptionAuthTokenMockAdapter`는 `accountPasswordAuthToken`을 보지 않고 항상 통과시킨다. 이 mock을 P6의 `AccountPasswordAuthTokenVerifier`로 교체한다. 계좌비밀번호 인증 mock은 이 파일 하나다(다른 mock 3개 — 가입 기존고객 확인, 자동·예약이체 계좌 상태 — 는 인증이 아니고 범위 밖).
- 토큰이 무효하면 `APW0102`를 낸다. Swagger 응답과 description(PR #476)도 함께 고친다.
- 근거: REQ-PRDT-010, `api_conventions.md` §6-3·§8-2

### PH-24. GL 분개 서비스 · 이체 기표 훅 · `gl.api`

**계약 — 9/30까지 문서로 먼저 낸다(P4·P2가 기다린다). 구현은 10/1에 착수한다** — S2 10/6~12에 PH-28 베이스라인과 겹쳐 4.5일이 들어가지 않는다.

```java
// gl.api
public interface JournalPostingUseCase {
    void post(JournalRequest request);   // 실패 시 예외를 던진다 — 삼키지 않는다
}
public record JournalRequest(String txType,         // gl_voucher.tx_type 값: OPENING · TRANSFER · PRODUCT_SUBSCRIPTION · INTEREST (타행은 PH-33-②가 추가)
                             String referenceKey,   // 원 거래번호 — 원장·이체와 전표를 잇는 키
                             LocalDate tradeDate,
                             List<JournalLine> lines) {}
public record JournalLine(String accountCode, JournalDirection drCr, long amount) {}
public enum JournalDirection { DEBIT, CREDIT }   // gl.domain 에서 gl.api 로 옮긴다 — 아래
```

- 헤더 금액은 두지 않는다. 금액은 줄마다 있고 전표 합계는 줄에서 계산한다(OPN처럼 줄 금액이 다른 전표가 있다).
- **`JournalDirection`을 `gl.domain`에서 `gl.api`로 옮긴다.** 지금은 `gl.domain`에 있는데(PR #478 머지분), `#349`가 건 `Api mayNotAccessAnyLayer()` 때문에 **`gl.api`가 `gl.domain`을 참조하면 `LayerArchitectureTest > gl`이 깨진다.** 공유 어휘를 소유 도메인의 `api`에 두는 것은 [ADR-0004](../adr/0004-domain-contract-surface.md) 결정 2와 같은 방향이다. `String drCr`로 두면 규칙은 피하지만 호출하는 트랙(P2 PH-14 · P4 PH-33-②)이 문자열 오타를 컴파일에서 못 잡는다.
- **`gl_voucher`에 `reference_key` 컬럼을 새 V 파일로 추가한다**(PR #478 스키마에는 없다). 이 키가 없으면 "이체 1건당 전표 1건" 검증과 PH-28b 분개누락 탐지가 원장과 조인할 수 없다.
- 전표 생성 + 분개 기표 서비스, `product_gl_mapping`
- `GlLedgerPostingHook implements transfer.api.LedgerPostingHook` — 이체 유형별 패턴표로 전표 1건. **예외를 던진다(= 이체 롤백).**
- 상품가입 초입금 기표(`LedgerPair.forProductSubscription` 완료 지점)
- 훅 전/후 이체 성능 저하율은 P1 PH-30 harness로 잰다.
- **완료 기준:** 이체 1건당 전표 1건, 차대변 일치. 훅에서 예외가 나면 이체가 롤백된다(테스트).

### PH-28. 시산표 API · 베이스라인

- 기간별·계정별 차변/대변 합계를 네이티브 SQL로 낸다.
- 600만 분개 원시집계 시간 베이스라인과 실행계획(10/8). PH-24 전에는 시드 분개만으로 잰다.
- 관리자 시산표 API(`GL_READ`). **계약이 두 층이라 섞지 않는다.**
    - 분개 저장 계약: `gl_journal_entry` 한 줄 = 계정 · `dr_cr`(DEBIT/CREDIT) · `amount`. FE 목(#158)이 이 모양을 따른다.
    - 시산표 응답 계약: 계정별 집계 행 = `accountCode` · `accountName` · `accountClass` · `normalBalance` · `debitTotal` · `creditTotal` · `entryCount`. FE `TrialBalanceRow`(`src/entities/gl/lib/aggregate-trial-balance.ts`)가 이 모양이다. 필드명을 바꾸려면 FE와 먼저 맞춘다.

### PH-75. 정보계 마트 설계

- 마트 2종: `mart_daily_account_balance`(일별 계정잔액), `mart_product_subscription`(상품별 가입·해지)
    - **as-of 컬럼 필수, PII 컬럼 없음**(고객은 가명 ID)
- 적재 주기, 읽기 전용 원칙, 정합성 기준(마트 합계 = 원장 합계)
- 조회 API 스키마 초안을 P5 PH-77과 FE ADM-03에 넘긴다.
- **10/16에 넘긴다.** P5가 10/26~27 컷오버 사이에 PH-76·77을 소화해야 해서 10/21에서 앞당겼다.
- **범위 밖:** 마감잔액 테이블

### GL-IDX. 시산표 인덱스 개선

`gl_journal_entry(account_code, trade_date)` 인덱스 1건을 추가하고 단일 EC2에서 다시 잰다. 마감잔액 테이블은 범위 밖이다.

### PH-28b. 회계 결함 주입 · 탐지율

- 결함 주입 스크립트 4종: 금액변조 · 분개누락 · 편측기표 · 중복기표. 테스트 프로필 전용이다.
- 탐지 수단: P4 대사 + 전표 차대변 검증 + 시산표 불일치 → 탐지율
- 결과 표(리포트에 싣는다)와 회계 ADR 1건
- **범위 밖:** 탐지 주기 단축, FE 결함 주입 탭

### FE ADM-02. 시산표 (#128)

화면은 목으로 완료됐다(FE PR #152). 남은 일은 PH-28 실연동과 계약 정정(FE #158)이다.
**완료 기준:** ADM-02가 목 없이 PH-28 응답으로 그려지고, FE `requirements-admin.md` REQ-ADM-020~025 인수기준을 통과한다.

### FE C-6. 화면 라벨 ↔ 용어 대조

AI로 불일치 목록을 뽑고 고친다. **C-1보다 먼저 한다.**
**완료 기준:** 불일치 목록이 문서로 남고, 목록의 항목이 전부 고쳐졌거나 사유와 함께 보류로 표시된다.

### FE C-2. 영업일·거래일 표기

예약·자동이체 화면에 **거래일만** 표기한다. 로그인 훅 교체는 FE #136(P1)이다.
**완료 기준:** 예약·자동이체 화면의 일자 라벨이 glossary의 "거래일"이고, 값이 서버 `tradeDate`와 같다.

### FE ADM-04. 대사 불일치 목록 (#130-①)

관리자 골격 위에 올린다. 권한은 `AUDIT_READ`다. PH-38 S2의 목록 API가 선행이다. **지금은 불일치를 로그로만 남겨 목록 API가 읽을 테이블이 없다**([PH-38](#ph-38-원장-대사-배치)).
**완료 기준:** FE `requirements-admin.md` REQ-ADM-040~042(PH-38 목록 API 스키마가 나온 뒤 P3가 채운다)를 통과한다.

### FE B-8. 타행이체 입력·결과

은행 선택 · 미결제 상태 · TIMEOUT 안내. 은행 목록·미결제 스키마는 PH-33에서 10/16까지 받는다. 편집권도 넘겨받았고 PR·머지는 P3가 한다.
**완료 기준:** 모의 대외계 정상 응답이면 완료 화면, 무응답이면 TIMEOUT 안내(재시도 버튼 없음)가 뜬다. 은행 목록은 서버 응답에서 온다.

### FE 조회 사유 입력

ADM-01 검색·상세·변경 요청에 사유 코드 선택을 붙이고 `X-Access-Reason` 헤더로 보낸다([PH-89](#ph-89-감사-5w--조회-감사--조회-사유-강제) 규약). FE `requirements-admin.md` POL-A09를 이 규약으로 고친다.
범위는 ADM-01의 세 경로(검색 · 상세 · 변경 3종)에 사유 선택 UI, 헤더 전파, 400 재요구 처리, #127 골격 편입까지다.
**완료 기준:** FE `requirements-admin.md` REQ-ADM-019를 통과한다.

### PH-09. UAT 시나리오 실행

- 10/30 배포본으로 가입 → 당행·타행 이체 → 상품가입 → 만기·해지를 돌린다.
- 관리자 흐름: 로그인 → 계정 운영 → 시산표 → 대사 불일치
- 팀 전체가 반나절 수동 실행하고 통과율과 결함 목록을 낸다. 계정찾기 흐름은 제외한다.

### PH-48. 발표 자료

6인 지표 종합표(개선 전 → 후), 아키텍처 도해(4계 + 관리자 채널 + 모의 대외계 + 정보계 + 아웃박스), ADR 목록.

### FE ADM-03. 정보계 대시보드

숫자 3개와 기준시점 표기, 차트 없음. 권한은 `GL_READ`다([D-09](README.md#d-09--923--리드)).
**완료 기준:** FE `requirements-admin.md` REQ-ADM-030~033을 통과한다.

### FE B-1①. 상품 RAG 챗

상품상세 화면 안에 질의·답변·면책 고지를 둔다. P3가 면책 문구를 리뷰한다. **범위 밖:** B-1②(근거 인용 UI · 거부 UI · PII 경고 · degrade UI)
**완료 기준:** 상품상세에서 질문하면 PH-65 응답의 `answer`와 `disclaimer`가 보이고, `refused=true`면 거부 문구가 보인다. AI 서버가 죽어도 상품상세의 다른 기능은 동작한다.

## 2-4. P4 태스크 상세

### PH-99. 이체 파이프라인 확장점(seam)

**`TransferExecutionService.execute()`는 P4만 고친다.** 다른 트랙은 아래 훅에 구현체만 등록한다.

```
TransferExecutionService.execute(command)                         ← 편집자: P4만
 ├─ [훅 A] TransferPreCheck 체인 (락 이전 · 읽기 전용 · order 순)
 │     └─ P2 PH-90 AvailableBalancePreCheck
 ├─ 계좌 락 · 한도 · 잔액 · 채번 (기존)
 ├─ requiresNewTransactionTemplate.execute {                       ← PH-80이 S2에 재작성
 │     원장 기표 LedgerPair (기존)
 │     [훅 B] LedgerPostingHook.afterLedger(ctx)                   ← P3 PH-24 (예외 → 롤백 = 이체 실패)
 │     transfer.tradeDate = businessDateProvider.today()          ← P5 provider, P4가 대입
 │     publisher.publishEvent(new TransferCompleted(...))         ← P1 타입, EVT-2
 │   }
 └─ failTransfer() REQUIRES_NEW { ERROR 저장; publishEvent(new TransferFailed(...)) }

[BEFORE_COMMIT 리스너 — P1 PH-96] 아웃박스 INSERT (JdbcTemplate). 예외 → 원본 롤백
```

| 훅 | 인터페이스 | 등록자 | 트랜잭션 |
|---|---|---|---|
| A | `transfer.api.TransferPreCheck { int order(); void check(TransferPreCheckContext ctx); }` — `record TransferPreCheckContext(long customerId, long withdrawalAccountId, long amount)`. 거부는 `BusinessException` | P2 PH-90 | 락 이전, 읽기 전용 |
| B | `transfer.api.LedgerPostingHook { void afterLedger(LedgerPostingContext ctx); }` — `record LedgerPostingContext(String transactionNumber, String txType, long amount, long fromAccountId, long toAccountId, LocalDate tradeDate)` | P3 PH-24 | 이체 REQUIRES_NEW 안. 예외 = 롤백 |
| tradeDate | `business.api.BusinessDateProvider` 주입 | 제공 P5 PH-40, 대입 P4 | 같은 트랜잭션 |
| 이벤트 | 완료는 템플릿 콜백 안, 실패는 `failTransfer()` 안 | P4 EVT-2 | 활성 트랜잭션 안이어야 리스너가 받는다 |

- **머지 순서(강제):** PH-99(10/2) → PH-80(10/8) → PH-90 → PH-24 → PH-41 대입(10/12~16, P4가 순서대로 머지) → PH-33-②(10/23). 이 창에서 순서대로 머지하려면 **PH-90·PH-24 PR은 10/12까지 리뷰 가능한 상태로 올린다.** PH-28 API(10/16)가 PH-24 위에 서므로 PH-24는 늦어도 10/14에 머지돼야 한다. 이 순서를 벗어나 `execute()`를 고치는 PR은 P4가 리뷰에서 막는다.
- **GL이 이벤트가 아니라 동기 포트인 이유:** 이벤트로 하면 전표가 원장과 다른 커밋에 들어가 "분개누락"이 정상 상태가 되기 때문이다.
- 산출물
    - `transfer.api`에 인터페이스·레코드(`TransferPreCheck` · `TransferPreCheckContext` · `LedgerPostingHook` · `LedgerPostingContext`)를 **구현체 0개인 채로** 머지한다. **`transfer.application`·`transfer.domain` 타입을 쓰지 않는다** — 기존 `api` 패키지(`batch.api`·`account.api`·`limit.api`)처럼 원시 타입만 쓴다. `TransferCommand`는 `application/port/in`에 있어 넘기지 않는다.
    - **입금 계약(10/16까지)** — P2 이자·만기·해지 입금용 `transfer.api` 계약(예: `LedgerDepositUseCase.deposit(accountId, amount, txType, referenceKey)`). 원장 기표를 포함한다. 지금 transfer의 공개 입금 계약은 상품가입용(`ProductSubscriptionDepositUseCase`) 하나뿐이다. 인일은 P4 완충에서 쓴다([D-14](README.md#d-14--92123--p4-제안-리드-승인)).
    - `execute()`에 자리 4곳을 주석으로 표시한다.
    - `docs/phase2/transfer_seam.md`에 정책을 적는다.
    - BEFORE_COMMIT 플러시 테스트 골격을 만든다(PH-32와 함께).
- 현황: `transfer.api` 패키지가 아직 없다. `api` 패키지가 있는 도메인은 limit · auth · otp · signup · terms · batch · account · customer다.

### PH-31. 고정길이 전문 규격

- 헤더: 전문길이 · 종별 · 거래코드 · 전송일시 · 기관코드 · 일련번호 · 응답코드
- 본문: 계좌 · 금액 · 예금주 · 적요
- 패딩 규칙을 1장으로 정리한다. 직렬화 코드는 PH-33에서 만든다.

### PH-38. 원장 대사 배치

**S1 완료 (PR #463, 9/23)**

- `LedgerReconciliationScheduler`가 매일 02:00(KST)에 전일 거래 계좌만 원장 합계와 `account.balance`를 대조한다.
- 불일치는 **ERROR 로그만** 남긴다. 자동 정정은 하지 않는다.
- 락 행 `V202609220900`. 자동·예약이체 배치 완료를 확인한 뒤 실행한다.

**S2 남은 일 (2일)**

- **불일치 저장 테이블** — Flyway + `schema_reference.md` + ERD. 목록 API와 FE ADM-04가 읽을 데이터다.
- 불일치 목록 조회 API(관리자, `AUDIT_READ`)
- 300만 원장 대사 시간 베이스라인(10/6). 전수 스캔 경로는 #468이다.
- **`CobStep` 편입** — P5 인터페이스(10/6)가 들어오면 독립 스케줄러를 대사 스텝 구현체로 옮긴다. 등록 주체는 P4이고, soft/hard 임계는 P5가 정한다.

**완료 기준:** 목데이터에서 불일치 0건. 실행시간 기록. 결함 주입(PH-28b) 불일치가 목록 API로 조회된다.

### #379 규격 전달

- 파티션 프로시저의 이름 · 시그니처 · 호출 주기를 P5에 보낸다.
- 결론도 함께 보낸다: **2차 기간에는 호출이 필요 없다.** `V202609132350`이 2027-12까지 선반영돼 있다. 따라서 PH-43 체인에서 파티션 스텝을 뺀다.

### PH-80. TIMEOUT · 정정 체인 · 자동 재시도 금지

- **순서:** 10/8 머지까지 S2 영업일이 3일(10/6~8)뿐이라 4일 작업이 들어가지 않는다. 그래서 둘로 나눈다. `ProcessResultStatus`와 무관한 정정 체인·INVALID·역행 금지·재시도 금지 규약은 **S1에 PH-99 직후 착수**한다. TIMEOUT 추가만 P5 회차 현황(10/6)을 받은 뒤 한다. **그래서 PH-99는 9/30까지 끝내는 것을 목표로 한다.** PH-99가 10/1 이후로 밀리면 S1분도 S2로 넘어가므로, 10/8 머지 범위를 P1(B-9)·P5와 다시 맞춘다.
- `ProcessResultStatus`(현재 SUCCESS · ERROR · PROCESSING)에 **TIMEOUT**을 추가한다. `transfer.status` 컬럼(VARCHAR(12), 값은 주석으로만 관리)의 주석을 Flyway로 바꾸고 `schema_reference.md`·ERD를 고친다.
    - 이 enum은 자동이체 실행 기록도 공유한다.
    - **P5가 10/6에 주는 "회차 상태 사용 현황 + 배치 회차는 TIMEOUT을 쓰지 않는다" 문서를 PR 본문 근거로 단다. PR 리뷰는 P5가 한다.**
- 원거래를 **INVALID**로 표기하고 `ref_transfer_id`를 둔다. 정정 체인은 원거래 INVALID → 취소정정 INSERT → 정상거래 INSERT다(DELETE 0건).
- 상태 역행 금지 검증, 자동 재시도 금지 규약 문서와 리뷰 체크 항목
- TIMEOUT 응답 스키마를 10/8까지 FE B-9(P1)에 넘긴다.
- **완료 기준:** 정정 시나리오가 DELETE 없이 끝난다. 체인을 따라 과거를 재구성할 수 있다.

### EVT-2. `publishEvent` 두 줄

완료 이벤트는 REQUIRES_NEW 템플릿 콜백 안에, 실패 이벤트는 `failTransfer()` 안에 넣는다. P1 플러시 통합 테스트가 통과해야 한다.

### HOOK-MERGE. 훅 구현체 머지

PH-90 PreCheck → PH-24 훅 B → PH-41 tradeDate 대입 순서로 머지한다. 이체 성능 저하율은 P1 harness로 잰다.

### PH-33. 대외계 (① 모의 서버 S1 → ② 타행 이체 S3)

**①**

- 별도 프로세스 모의 서버(응답 지연 · 무응답 · 중복응답 스위치)
- `adapter/out/external` 전문 직렬화·역직렬화 + 단위 테스트

**②**

- 10/19~23 5일에 5인일이라 여유가 없다. 기표와 무관한 송신·조회거래·Resilience4j는 S2 10/13~16에 착수한다.
- 송신: 당행 출금 → 전문 송신 → 응답 → 완료/TIMEOUT
- **조회거래**: 무응답이면 원거래 상태를 조회로 확인한다. 자동 재시도가 아니다.
- **미결제타점권 기표 2패턴**: 요청 시 예수금 → 미결제타점권(`10200`), 확정 시 해소. 훅 B에서 `gl.api`를 부른다. 차대 방향은 P4가 정해 [gl_journal_patterns.md](gl_journal_patterns.md)에 적는다. **`gl_voucher.tx_type` CHECK에 타행 유형이 없으므로 새 V 파일로 CHECK를 넓힌다**(기존 마이그레이션 수정 금지).
- Resilience4j 타임아웃·서킷브레이커 — 타행만 막고 당행은 계속 돈다.
- 은행코드 시드 5개. 은행 목록·미결제 상태 스키마를 10/16까지 FE B-8(P3)에 넘긴다.
- 타행 확정 시점에도 `transfer.completed`가 발행된다.
- **완료 기준:** 정상 흐름과 "무응답 후 조회 확인" 흐름이 돈다. 모의 서버가 무응답이어도 당행 TPS가 유지된다.
- **범위 밖:** 미결제 정산(PH-35), 타행 수수료(`FEE = 0L` 유지)

### PH-36. 장애 주입 3종 · 보상 트랜잭션

- 3종: 타임아웃 · 무응답 · 중복응답. 즉시 롤백하지 않고 조회한 뒤 판단한다. 필요하면 보상 트랜잭션을 발행한다.
- 자금 정합성 유지율: 원장 합계 = 잔액 합계 + 미결제
- **완료 기준:** 3종 모두 정합성 100%
- **범위 밖:** 부분실패 · 순서역전 · 지연

### LOCK-TUNE. 락 튜닝

P1 PH-30 결과(데드락률 · p99)를 근거로 락 범위나 조회 순서를 1~2건 조정한다. S4 재측정은 P1에 요청한다. S4는 코드 금지라 이 작업은 S3에 남는다.

### PH-36b. 최종 측정 · 리포트

- P1에서 단일 EC2 재측정 수치를 받는다.
- 3-Tier TPS·p99 **한 줄**(컷오버 실패 시 Plan B)
- `execute()` 순환복잡도 재측정(반나절)
- 정합성+성능 리포트

## 2-5. P5 태스크 상세

### PH-40. 영업일 도입 (+ PH-41 거래일 컬럼)

**계약 (10/2):**

```java
// business.api
public interface BusinessDateProvider {
    LocalDate today();
    LocalDate nextBusinessDay(LocalDate date);
    boolean isBusinessDay(LocalDate date);
}
```

- `business_date` 테이블, 주말 + 2026 공휴일 시드(#471). Flyway + `schema_reference.md` + ERD
- 거래일 컬럼 `transfer.trade_date`·`ledger_entry.trade_date` (#472). 값 대입은 P4가 10/16에 한다. 휴일·마감 후 거래는 익영업일로 한다.
- 시각 호출을 감사하고 ArchUnit으로 막는다(#473). 현황(`src/main`): 인자 없는 `LocalDateTime.now()` 9곳, `now(KST)` 2곳, `Clock` 주입 `now(clock)` 43곳.
    - **금지:** 인자 없는 `now()`와 `now(ZoneId)`.
    - **허용:** `Clock` 주입 호출. 발생 시각(`occurredAt` 등) 용도다.
    - **영업일·거래일은 `BusinessDateProvider`로만 구한다.** `LocalDate.now(clock)`으로 대신하는 것은 ArchUnit으로 못 잡으므로 리뷰 항목으로 둔다.
- test 프로필 전용 영업일 이동 훅
- **범위 밖:** 휴일 등록 API, 처리일·기표일(거래일만)
- **완료 기준:** 영업일을 옮기면 배치 · 이자 · 전표번호가 그 날짜를 따른다.

### PH-50. 네트워크 Terraform

- VPC `10.30.0.0/16`, 2AZ, 서브넷 6개(public / app / data × 2), 라우팅 3종, **NAT 1개**
- SG: ALB→WAS 8080, WAS→RDS 3306, WAS→util 6379/8000. NACL 기본
- Terraform 루트, 원격 상태(S3 + lock), 모듈 자리: `modules/network`·`modules/compute`(P5), `modules/data`·**`modules/security`**(P2)
- **출력값 → P2 (10/2):** 서브넷 ID, SG ID
- **완료 기준:** plan 통과. data 서브넷에 아웃바운드 없음.

### PH-85. ADR — 미도입 결정

Spring Batch → CobStep 체인 + 실행 기록, MQ → 아웃박스 폴링(P1), API Gateway → ALB + Spring Security로 대체한다는 결정이다.

### PH-94. Expand-Contract 규약

`docs/api_conventions.md`에 1절을 추가한다. "2차 기간에는 응답 필드를 제거하거나 형태를 바꾸지 않는다. 바꿀 때는 FE 스냅샷 PR을 동반한다." Flyway 리뷰 항목에 `DROP`·`RENAME` 금지를 넣는다.

### PH-43. COB 파이프라인

**계약 (10/6 레포에 먼저 머지):**

```java
// batch.api
public interface CobStep {
    String name();
    int order();
    void run(LocalDate businessDate);   // 각 스텝은 @Transactional 자기 경계를 연다
}
```

**스텝 체인 (순서 소유 P5):**

| 순서 | 스텝 | 구현 소유 | 등록 시점 |
|---|---|---|---|
| 1 | 영업일 확인 | P5 | S2 |
| 2 | 자동/예약이체 (기존 `DailyTransferBatchService` 감싸기) | P5 | S2 |
| 3 | 대사 | P4 PH-38 | 10/6 인터페이스 머지 후 |
| 4 | 만기 감지 | P2 PH-15 | 10/23 |
| 5 | 휴면 전환 | P2 PH-16 | 자리만 10/16 · 등록 10/30까지 |
| 6 | 정보계 ETL | P5 PH-76 | S3 |
| 7 | 영업일 전환 | P5 | S2 |

- **파티션 생성 호출 스텝은 없다**([#379 규격](#379-규격-전달)).
- 러너: 기존 스케줄러에서 순서대로 부르고 `BatchExecutionLockPort`로 단일 실행한다.
- 대사 정책: 불일치 ≤ 임계(예: 10)면 soft(리포트·로그), 초과면 hard(마감 중단). P3 PH-28b가 이 정책으로 검증한다.
- 배치 실행 기록 테이블(#371): job · step · 영업일 · 시작/종료 · 상태 · 실패 사유. **조회 API는 범위 밖**이다.
- 실패 스텝부터 다시 실행할 수 있다.
- COB 베이스라인: 15만 계좌 순차, 10/16, 단일 EC2
- **선행은 PH-40뿐이다.** 대사 배치를 기다리지 않는다(선행 순환 정정).
- **완료 기준:** 스텝이 순서대로 돌고 기록이 남는다. 대사 후 원장 합계 = 잔액 합계이고, 불일치 시 정책대로 동작한다.
- 현황: 러너·스텝 개념은 없다. `batch` 패키지에는 `BatchExecutionLockPort`(api)와 `DailyTransferBatchService`(+ 스케줄러·락 어댑터)만 있다. 대사(`transfer`)와 멱등키 정리는 각자 독립 스케줄러다.

**P4에 주는 것 (10/6):** 자동·예약이체 회차 상태 사용 현황 1장과 "배치 회차는 TIMEOUT을 쓰지 않는다"는 결론. PH-80의 전제다.

### PH-42. correlation id · 로그 마스킹 · JSON 로깅

- 요청 필터: 헤더 `X-Correlation-Id`(없으면 UUID) → MDC `correlationId` → 응답 헤더
- 로그 패턴에 `correlationId`와 `customerId`(마스킹)를 넣는다.
- JSON 구조화 로깅 + 파일 롤링, 로그 레벨 정책
- 로그의 계좌번호·연락처·주민번호를 마스킹한다(`MaskingUtil` 재사용).
- **완료 기준:** 민감정보가 노출된 로그 0건. correlation id 하나로 거래 전 구간을 추적할 수 있다.

### PH-57. 용량 산정

원장 300만·분개 600만·이체 행 크기로 스토리지, 목표 TPS, 인스턴스 타입을 표 1장에 정리한다. 여유를 일부러 적게 잡는다(병목이 드러나야 개선 서사가 선다).

### PH-47. 메트릭 · Grafana

- Micrometer + Prometheus. 대시보드: 거래 · 배치 · JVM · DB
- 알람 3원칙: 노이즈 억제 · 실행 가능성 · **Runbook 링크 필수**
- actuator 노출 범위 재설계. 관리자 화면에 거래 메트릭을 중복으로 만들지 않는다.
- **완료 기준:** 알람 발생부터 인지까지 시간을 잰다.
- **범위 밖:** P4 PH-39(메트릭 소비 측)

### PH-51. 컴퓨트 계층 · 컷오버

- ALB(2AZ) + 대상그룹. 헬스체크는 `/actuator/health`이고, QA 시드 검증이 헬스체크를 죽이지 않게 한다(#439·#445).
- WAS ASG: 시작 템플릿, min 1 / max 2. util 노드 1대(app 서브넷)에 Redis와 FastAPI(P6)를 docker로 올린다.
- IAM은 인스턴스 프로파일 2개(WAS · util)와 SSM Agent·Session Manager 접속 기반까지다. 나머지 IAM은 P2 PH-72가 맡는다([D-11](README.md#d-11--920--p2p5)).
- 현행 SSM 배포 스크립트(#440 반영분)를 이관한다.
- HTTPS 리스너는 P2가 넘기는 ACM certificate ARN으로 만든다.
- **출력값 → P2 (10/26 apply 직후):** ALB ARN · 리스너 ARN · ALB DNS name · ALB zone ID
- 일정: 10/19~23 리허설 → 10/26 apply → P2 RDS 복원 → SG 교차 검증 → 10/27 DNS 전환. 기존 EC2는 11/6까지 유지한다.
- **Plan B:** EC2를 유지하고 구축 진행률을 리포트한다.
- **완료 기준:** 프라이빗 서브넷에서 인터넷에 직접 접근할 수 없다. 앱이 3-Tier에서 돈다.

### PH-76. 정보계 ETL

- **10/28 PH-77까지 가려면 PH-76은 10/19~23 리허설과 병행한다**(10/26~27은 컷오버).
- COB 후속 스텝으로 마트 2종을 적재한다. 전일 확정분 증분이고 재적재가 가능하며, **PII는 적재하지 않는다.**
- 적재 실패는 soft다. 300만 원장 적재 시간을 기록하고, 마트 합계 = 원장 합계를 P3와 함께 검증한다.

### PH-77. 정보계 조회 API

- 일별 수신 잔액 추이, 상품별 가입/해지, 채널별 거래량(`GL_READ`)
- **모든 응답에 `asOf` 필드**가 있고 개인정보는 없다. p95·p99를 기록한다.

### PH-45. COB 병렬화 · #365

- 자동/예약이체와 대사 스텝을 청크로 바꾸고, 청크 크기를 튜닝한 뒤 병렬 스텝으로 전환한다.
- 스텝 단위 실패 격리(한 계좌 실패가 전체를 죽이지 않는다)
- #365: 재확정 배치를 별도 스케줄로 분리한다.
- 순차 → 청크 → 병렬 3단계를 단일 EC2에서 잰다.

### PH-56. WAS 스케일아웃 비선형성 실증

- **측정 환경 예외** — 3-Tier(WAS 2대)에서만 성립한다([README §3-1](README.md#3-1-판정과-측정-환경)).
- P1 harness를 `BASE_URL = ALB DNS`로 돌려 WAS 1대 → 2대의 증가율을 잰다. 일반(계좌 분산)과 핫스팟(동일 계좌 동시 100건) 두 곡선이다.
- **완료 기준:** 핫스팟의 2대 확장 증가율이 일반보다 유의하게 낮다는 것을 수치로 보인다.

### PH-55. 배포 파이프라인 · destroy 리허설

S4지만 CI/CD·문서·훈련이라 S4 규칙에 걸리지 않는다([D-05](README.md#d-05--923--리드)).

- 환경 3단계(dev / staging / prod)
- 무중단 배포(ASG 롤링 + 헬스체크)와 롤백 절차
- **개발 권한 ≠ 배포 권한**(승인 이력은 Git에). 배포 Job의 성패가 실제 배포 성패와 같아야 한다(#440). 릴리스 노트를 쓴다.
- 배포 다운타임 측정(롤링 교체 1회)
- **`terraform destroy` 리허설 1회.** 실행 주체는 P5다([README §3-8](README.md#3-8-3-tier-수명과-destroy)).

## 2-6. P6 태스크 상세

### PH-60 · PH-60b. 목데이터

| 단계 | 기한 | 고객 | 계좌 | 원장 | 분개 | 그 외 |
|---|---|---|---|---|---|---|
| PH-60 | 9/30 | 1만 | 3만 | 50만 | 개시 잔액 전표 1건(GL 테이블이 9/30 dev에 없으면 PH-60b로) | 자동이체 5천, 만기 임박 소량 |
| PH-60b | 10/2 | 5만 | 15만 | 300만(3개월) | 600만 | 자동/예약이체 2만, MATURED·휴면 후보 |

- PH-60b 거래 패턴: 급여일 집중, 주말 감소, **동일 계좌 집중 구간**(PH-56)
- 분개는 [gl_journal_patterns.md](gl_journal_patterns.md)의 `OPN`·`TRF`·`SUB` 패턴으로 원장 1건당 전표 1건을 만든다. 순서는 계정 시드 → 개시 전표 → 거래 전표다. **전표번호 일련은 6자리**다. **`SUB`의 차변은 예수금이다(현금성 아님).** `INT`와 타행 미결제는 만들지 않는다. 적재 후 같은 문서 §5의 자가 검증 SQL을 돌린다 — (2)는 두 합계가 같아야 하고 나머지는 0행이어야 한다.
- 재현 가능한 시드값을 쓰고, QA 데모 대역(`V202609161800`, #439·#444)과 겹치지 않게 한다.
- **P4 검수를 통과해야 완료다**(원장 짝 · 원장 합계 = 잔액 합계 · 전표 차대변). 적재 시간을 기록한다.
- **10/6이 한계선이다.** 그보다 늦으면 10/8 개선 전 수치 마감(P2·P3·P4·P5)을 지킬 수 없다.
- GL 쪽 선행은 머지 전에도 쓸 수 있다. 테이블은 PR #478(PH-20), 패턴·채번은 PR #491(PH-21)에 이미 있으므로 그 브랜치 기준으로 생성기를 먼저 짠다. 10/1 머지를 기다리지 않는다.
- 누가 쓰나: PH-60은 P2 적수 · **P1 TPS·동시성** · P1 아웃박스 시뮬레이션. PH-60b는 P4 대사(10/6) · P3 시산표(10/8) · P5 COB(10/16).

### PH-22'. RAG 문서 선정

- 문서 3~5개: 은행연합회 표준약관(예금·적금), 금융소비자보호 관련 1건, 자사 `terms`
- PDF로 구할 수 있는 것만 쓴다(HWP 제외). P3가 30분 리뷰한다.
- 조항 단위(`제N조 ① ②`) 메타데이터 스키마 1장

### PH-61. 조항 파서 · 색인

- PDF 파싱 + 조항 파서. 샘플 30개를 육안 검수한다.
- OpenSearch 로컬 docker + Nori, 인덱스 매핑(본문 · 조항 메타 · kNN), 임베딩 1종
- 벡터 단독 베이스라인(10/16)

### PH-29'. 평가셋 · 응대 정책 · 프롬프트 v1

- 답하는 것: 약관·절차. 안 하는 것: 추천 · 수익 단정 · 세후 확정 · 타행 비교
- 평가셋 80문항(거부 15 포함) — AI 초안 + 검수, 정답 근거 조항 태깅
- 프롬프트 v1: 근거 인용 강제, 인용이 없으면 거부, 면책 문구. 면책 문구는 P3가 리뷰한다.

### PH-49c. 세션 외부화

- `spring-session-data-redis` + `@EnableRedisHttpSession`. 만료는 관리자 30분 / 고객 10분을 유지한다.
- `SecurityContext` 직렬화를 확인하고, 로그아웃 시 세션을 삭제한다.
- WAS 2 프로세스 로그인 유지 테스트(로컬 docker). P5 util 노드에서는 10/28에 15분 확인을 한다.
- `SecurityConfig`는 PH-49a-② 위에서 고친다(10/12~).
- **ALB 스티키 세션은 쓰지 않는다.**
- 현황: `SecurityConfig`가 `HttpSessionSecurityContextRepository`를 쓴다.

### PH-92. MFA · Step-up 매트릭스

로그인 비밀번호 · OTP · 계좌비밀번호 · 고객 기준 계좌비밀번호 토큰(#443)을 지식/소유/생체로 분류한다. 이체의 2요소 여부와 미달 구간을 매트릭스 1장으로 낸다. 구현은 범위 밖(B-10)이다.

### PH-65. RAG 서빙 (① 스키마 10/16 → ② 구현 10/28)

**① 계약:**

```
POST /ai/chat { question } → { answer, citations[], refused, disclaimer }
```

**②**

- FastAPI 엔드포인트를 util 노드 docker에 올린다(그 전에는 로컬).
- Spring `adapter/out/ai` 포트·어댑터
- **degrade**: 타임아웃 3초, 실패하면 정해진 안내를 준다. 거래·가입 플로우로 번지지 않는다.

### PH-62. 하이브리드 검색

BM25(Nori) + kNN 점수 합산. 벡터 단독 vs 하이브리드 2종의 정밀도·재현율을 비교한다. 리랭킹은 범위 밖이다.

### PH-63. LLM · 근거 검증 · 평가 자동화

LLM 호출 + 근거 검증 노드(인용 없으면 거부), LangSmith 트레이싱. 80문항을 명령 한 번으로 돌리는 평가 스크립트로 v1 수치를 낸다(10/23).

### PH-73. 가드레일

- 입력 필터: 주민번호·계좌번호·연락처를 마스킹한 뒤 LLM에 보낸다.
- 거부 규칙 3개(추천 · 수익 단정 · 타행 비교), 답변 후처리로 면책 문구를 붙인다.
- 질의·응답 로그 테이블(마스킹 상태). 조회 API는 범위 밖이다.

### PH-66. 최종 평가

80문항 v1 → v2, 거부 15문항 차단율, PII 50건 전달 0건, 지연 p95·p99, RAG 품질 리포트.

---

## 3. 1차 잔여 — 2차 인일 밖

1차에서 넘어온 일이다. 2차 인일과 지표에 넣지 않고 담당자가 재량으로 처리한다. 2차 태스크에 영향을 주는 것만 비고에 적는다.

| 이슈 | 제목 | 담당 | 비고 |
|---|---|---|---|
| #406 · #407 · #408 | 멱등키 원자성 · PROCESSING 재확정 배치 · 헬퍼 정리 | P1 | #406을 고칠 때 PH-99의 3-트랜잭션 창을 본다 |
| #197 | 최근 이체 목록 조회 | P4 | — |
| #376 · #469 | 이체결과 CSV 다운로드 · 스트리밍 | P4 | PR #470 |
| #311 | `/auth/logout` Swagger 노출 | P4 | PH-03-②(S4)가 기다린다 |
| #431 | CI Gradle 캐시 | P4 | — |
| #439 · #445 | QA 시드 검증이 prod 기동을 막음 | P4 | PH-51 헬스체크 전제 |
| #350 | 헥사고날 가이드에 `api/` 규약 | P4 | #359 결정 뒤 |
| #363 | `@MockitoBean` 후속 | P3 | — |
| #467 | 출금계좌 사용 여부 조회를 autotransfer 계약으로 | P2 | — |
| #464 · #465 · #479~#488 · PR #490 | 인증·비밀번호 정책 보완 11건 | P6 | #465는 비밀번호 변경 재인증용 Redis 레이트리밋 |
| #372 | 감사로그 upfront logging 검토 | P5 | PH-46 범위 밖 — 참고 자료로 열어 둔다 |
| FE #100 · #102 · #105 | 계정찾기 · 비밀번호 변경 연동 | P2 (#105는 담당 미지정) | 서버 #326~#328 완료 |
| FE #109 · #121 | 환경변수 가드 · 연동 목록 | P1 | — |
