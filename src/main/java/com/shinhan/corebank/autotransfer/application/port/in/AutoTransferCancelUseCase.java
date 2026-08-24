package com.shinhan.corebank.autotransfer.application.port.in;

import java.util.List;

public interface AutoTransferCancelUseCase {
    // 요청한 id 순서(오름차순 정렬·중복 제거된 순서)대로 건별 결과를 돌려준다
    List<AutoTransferCancelResult> cancel(AutoTransferCancelCommand command);
}
