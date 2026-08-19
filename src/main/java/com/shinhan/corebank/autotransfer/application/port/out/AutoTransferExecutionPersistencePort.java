package com.shinhan.corebank.autotransfer.application.port.out;


import com.shinhan.corebank.autotransfer.domain.AutoTransferExecution;

import java.util.List;

public interface AutoTransferExecutionPersistencePort {
    // 즉시 flush 가 되어야 함 - 유니크 제약이 호출 시점에 바로 걸려야 중복 실행 막음
    // flush란? -> 이 메모리에 쌓아둔 걸 실제 DB에 내보내는 동작
    // 주의 : 유니크 제약 위반이 나면 그 시점에 현재 트랜잭션은 이미 커밋 불가 상태가 됨
    // -> 호출부가 이 예외를 같은 트랜잭션 안에서 catch하고 계속 진행하면 안됨
    // 그대로 던져서 트랜잭션을 롤백시키고, 이미 처리됨 판단은 트랜잭션 밖에서 해야함
    AutoTransferExecution save(AutoTransferExecution execution, Long autoTransferId);

    // 재확정 배치 대상 - PROCESSING 상태로 멈춰있는 회차 전체 ( 부모 AutoTransfer 포함 )
    List<StuckExecution> findAllProcessing();

    // 연속 실패 감지용 - 자동이체의 최근 실행이력을 최신순으로 limit개만 조회
    List<AutoTransferExecution> findRecentByAutoTransferId(Long autoTransferId, int limit);
}
