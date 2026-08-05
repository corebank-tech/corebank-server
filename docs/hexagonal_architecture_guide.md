# CoreBank 헥사고날 아키텍처 흐름 가이드 (Spring Data JPA 기준)

이 문서는 REST API 요청이 들어왔을 때, 헥사고날 아키텍처(Hexagonal Architecture) 패턴에 따라 시스템의 제어 흐름이 어떤 패키지와 파일들을 거쳐 데이터베이스까지 도달하는지 팀원들이 쉽게 이해할 수 있도록 작성된 가이드입니다.

---

## 🚀 시나리오: "이체 실행 API (`POST /api/v1/transfers`)" 요청 흐름

클라이언트가 이체 요청 API를 호출했을 때, 제어권(Control Flow)이 밖에서 안으로, 그리고 다시 밖으로 빠져나가는 8단계를 설명합니다.

### 1단계: [Adapter In] Controller

- **파일 경로**: `src/main/java/com/shinhan/corebank/transfer/adapter/in/web/TransferController.java`
- **역할**: 외부 세계(HTTP)와 애플리케이션 코어를 연결하는 "웹 어댑터"입니다.
- **동작**:
    1. 클라이언트의 HTTP JSON 요청을 받습니다.
    2. 요청 데이터를 애플리케이션 계층이 이해할 수 있는 순수 자바 객체인 **`TransferCommand`**로 변환합니다.
    3. `TransferExecutionUseCase` (인터페이스)를 호출하여 처리를 위임합니다.

### 2단계: [Port In] UseCase (인터페이스)

- **파일 경로**: `src/main/java/com/shinhan/corebank/transfer/application/port/in/TransferExecutionUseCase.java`
- **역할**: 웹 계층이 애플리케이션 코어로 들어오기 위해 통과해야 하는 **"입구(Inbound Port)"**입니다.
- **동작**: "이체를 실행하라"는 단일 목적의 메서드 규격을 정의합니다. 웹 어댑터는 이 규격만 알고 호출합니다.

### 3단계: [Application] Service

- **파일 경로**: `src/main/java/com/shinhan/corebank/transfer/application/service/TransferService.java`
- **역할**: 실제 UseCase 인터페이스를 구현(implements)하는 비즈니스 오케스트레이터(지휘자)입니다.
- **동작**:
    1. DB에서 데이터를 조회하도록 Outbound Port에 지시합니다.
    2. **순수 도메인 객체(`Transfer.java`)**를 메모리에 올리고 비즈니스 로직을 실행합니다.
    3. 로직이 끝나면 다시 Outbound Port를 통해 DB 저장을 지시합니다.

### 4단계: [Domain] 순수 도메인 객체

- **파일 경로**: `src/main/java/com/shinhan/corebank/transfer/domain/Transfer.java`
- **역할**: 시스템의 핵심 비즈니스 룰을 담고 있는 가장 중요한 POJO 객체입니다.
- **동작**:
    - JPA 어노테이션이나 프레임워크 의존성이 전혀 없습니다.
    - "상태 변경", "유효성 검증" 등 진짜 비즈니스 로직은 Service가 아닌 이곳 도메인 객체 내부에서 수행되어야 합니다 (Rich Domain Model).

### 5단계: [Port Out] Persistence Port (인터페이스)

- **파일 경로**: `src/main/java/com/shinhan/corebank/transfer/application/port/out/TransferPersistencePort.java`
- **역할**: 애플리케이션 코어가 데이터베이스(외부)로 나가기 위해 통과해야 하는 **"출구(Outbound Port)"**입니다.
- **동작**: `save(Transfer transfer)` 처럼 도메인 객체를 저장/조회하라는 인터페이스 규격만 정의합니다. (Service는 이 인터페이스만 의존하며 DB가 MySQL인지 MongoDB인지 모릅니다.)

### 6단계: [Adapter Out] Persistence Adapter

- **파일 경로**: `src/main/java/com/shinhan/corebank/transfer/adapter/out/persistence/TransferPersistenceAdapter.java`
- **역할**: Outbound Port를 구현(implements)하여 실제 DB 접근 기술(Spring Data JPA)을 다루는 어댑터입니다.
- **동작**:
    1. Service로부터 넘어온 순수 **도메인 객체(`Transfer`)를 JPA 엔티티(`TransferJpaEntity`)로 변환(Mapper)**합니다.
    2. JpaRepository 인터페이스를 호출하여 DB 쿼리를 유발합니다.

### 7단계: [Adapter Out] Spring Data JPA Repository

- **파일 경로**: `src/main/java/com/shinhan/corebank/transfer/adapter/out/persistence/TransferJpaRepository.java`
- **역할**: Spring Data JPA가 제공하는 `JpaRepository`를 상속받은 인터페이스입니다.
- **동작**: 어댑터의 요청을 받아 실제 SQL을 생성하고 실행합니다.

### 8단계: [Adapter Out] JPA Entity

- **파일 경로**: `src/main/java/com/shinhan/corebank/transfer/adapter/out/persistence/TransferJpaEntity.java`
- **역할**: 데이터베이스 테이블과 1:1로 매핑되는 객체(DTO)입니다.
- **동작**: `@Table`, `@Column` 등의 어노테이션이 붙어 있으며, 도메인 로직 없이 단순히 DB 데이터를 담는 껍데기 역할을 합니다.

---

## 📊 의존성 방향 (매우 중요!)

헥사고날 아키텍처의 가장 중요한 원칙은 **"모든 의존성은 밖에서 안(Domain)을 향해야 한다"**는 것입니다.

```
[웹 어댑터] ────────▶ [인터페이스(Port In)]
                           │
                           ▼
                      [서비스(Service)] ────────▶ [도메인(Domain)]
                           │
                           ▼
[DB 어댑터] ────────▶ [인터페이스(Port Out)]
```

- `Adapter`는 `Port`를 의존합니다.
- `Service`는 `Port Out`과 `Domain`을 의존합니다.
- **`Domain`은 그 어떤 패키지(JPA, Web 등)도 의존하지 않습니다!**

이 원칙 덕분에 우리는 원장 DB를 MySQL에서 Oracle로 바꾸거나, 웹 API를 gRPC로 교체하더라도 **핵심 비즈니스 로직(Service, Domain)을 단 한 줄도 수정하지 않을 수 있습니다.**
