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
     * 그 영업일의 기존 행을 모두 지우고 mismatches로 바꾼다. 비어 있으면 그날 행이 없어진다.
     * 같은 영업일 재실행은 "그날 대사를 다시 했다"는 뜻이라 마지막 결과만 남긴다.
     */
    void replaceForBusinessDate(
            LocalDate businessDate, List<LedgerReconciliationMismatch> mismatches, LocalDateTime detectedAt);

    /** 불일치가 기록된 가장 최근 영업일. 기록이 없으면 비어 있다. */
    Optional<LocalDate> findLatestBusinessDate();

    long countByBusinessDate(LocalDate businessDate);

    /** 계좌 ID 오름차순. */
    Page<LedgerReconciliationMismatchView> findByBusinessDate(LocalDate businessDate, Pageable pageable);
}
