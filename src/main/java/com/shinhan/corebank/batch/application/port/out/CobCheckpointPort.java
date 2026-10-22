package com.shinhan.corebank.batch.application.port.out;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface CobCheckpointPort {

    // cobDate의 스텝 행을 모두 PENDING으로 남긴다. 이미 있는 행은 그대로 둔다(SUCCESS를 되돌리지 않는다)
    void initialize(LocalDate cobDate, List<String> stepNames, LocalDateTime now);

    // 스텝 행을 SUCCESS로 바꾼다. 행이 없으면 아무 일도 하지 않는다
    void markSuccess(LocalDate cobDate, String stepName, LocalDateTime now);
}
