package com.shinhan.corebank.transfer.application.port.out;

import com.shinhan.corebank.transfer.application.port.in.LedgerReconciliationMismatch;
import com.shinhan.corebank.transfer.application.port.in.LedgerReconciliationMismatchQueryUseCase.LedgerReconciliationMismatchView;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** COB 대사(PH-38)가 찾은 불일치를 영업일별로 남긴다. 관리자 목록 API가 읽는다. */
public interface LedgerReconciliationMismatchPort {

    /**
     * 그 영업일 행이 하나도 없을 때만 mismatches를 저장하고, 저장했으면 true를 돌려준다.
     *
     * 멈춘 COB를 재개하면 같은 영업일로 대사가 다시 도는데, 그때는 지금 원장을 대조한 다른 시점의 결과다.
     * 마감을 멈춘 원인을 조사할 수 있도록 그날 처음 저장한 목록을 덮어쓰지 않는다(#749).
     */
    boolean saveIfAbsent(
            LocalDate businessDate, List<LedgerReconciliationMismatch> mismatches, LocalDateTime detectedAt);

    /** 불일치가 기록된 가장 최근 영업일. 기록이 없으면 비어 있다. */
    Optional<LocalDate> findLatestBusinessDate();

    long countByBusinessDate(LocalDate businessDate);

    /** 계좌 ID 오름차순. */
    Page<LedgerReconciliationMismatchView> findByBusinessDate(LocalDate businessDate, Pageable pageable);
}
