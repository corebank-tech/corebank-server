package com.shinhan.corebank.limit.application.service;

import java.time.Clock;
import java.time.LocalDate;

import com.shinhan.corebank.limit.application.port.in.LimitQueryUseCase;
import com.shinhan.corebank.limit.application.port.in.dto.LimitResult;
import com.shinhan.corebank.limit.application.port.out.TransferLimitQueryPort;
import com.shinhan.corebank.limit.domain.TransferLimit;
import com.shinhan.corebank.limit.domain.TransferLimitDailyUsage;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LimitQueryService implements LimitQueryUseCase {

    private final TransferLimitQueryPort transferLimitQueryPort;
    private final Clock clock;

    /**
     * 한도 행이 없는 고객에게는 정책 기본값을 그대로 응답한다. 가입 시 기본값 부여
     * (REQ-TRSF-029)가 연결되기 전까지 행 없는 고객이 존재하는데, 실제 검증에 쓰이는
     * 값도 같은 기본값이므로 조회 결과와 어긋나지 않는다. 조회는 읽기 전용이라 이
     * 시점에 행을 만들지 않는다.
     * 당일 사용액 행은 그날 첫 이체 전까지 없으므로 사용액 0으로 본다.
     */
    @Override
    public LimitResult get(Long customerId) {
        TransferLimit limit = transferLimitQueryPort.findByCustomerId(customerId)
                .orElseGet(() -> TransferLimit.create(customerId));

        LocalDate today = LocalDate.now(clock);
        TransferLimitDailyUsage usage = transferLimitQueryPort.findUsage(customerId, today)
                .orElseGet(() -> TransferLimitDailyUsage.create(customerId, today));

        return LimitResult.from(limit, usage);
    }
}
