package com.shinhan.corebank.scheduledtransfer.application.port.in;

import java.util.List;

public interface ScheduledTransferCancelUseCase {
    // 요청한 id 순서(오름차순 정렬·중복 제거된 순서)대로 건별 결과를 돌려준다
    List<ScheduledTransferCancelResult> cancel(ScheduledTransferCancelCommand command);
}
