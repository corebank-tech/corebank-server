package com.shinhan.corebank.limit.application.service;

import java.time.Clock;
import java.time.LocalDate;

import com.shinhan.corebank.limit.application.port.in.TransferLimitQueryUseCase;
import com.shinhan.corebank.limit.application.port.in.dto.TransferLimitResult;
import com.shinhan.corebank.limit.application.port.out.TransferLimitQueryPort;
import com.shinhan.corebank.limit.domain.TransferLimit;
import com.shinhan.corebank.limit.domain.TransferLimitDailyUsage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransferLimitQueryService implements TransferLimitQueryUseCase {

    private final TransferLimitQueryPort transferLimitQueryPort;
    private final Clock clock;

    /**
     * 한도 행이 없는 고객에게는 정책 기본값을 그대로 응답한다. 가입 연계(REQ-TRSF-029)와
     * 백필을 붙여 운영에서는 행 없는 고객이 사라졌지만, 통합테스트나 시드처럼 회원가입
     * 흐름을 거치지 않고 만든 고객은 여전히 행이 없다. 그래서 오류로 올리지 않고 폴백을
     * 유지한다. 실제 검증에 쓰이는 값도 같은 기본값이라 조회 결과와 어긋나지 않는다.
     * 조회는 읽기 전용이라 이 시점에 행을 만들지 않는다.
     * 당일 사용액 행은 그날 첫 이체 전까지 없으므로 사용액 0으로 본다.
     */
    @Override
    public TransferLimitResult get(Long customerId) {
        TransferLimit limit = transferLimitQueryPort.findByCustomerId(customerId)
                .orElseGet(() -> {
                    // 가입 연계(REQ-TRSF-029)가 붙은 뒤로 이 로그는 데이터 결함 신호다.
                    // 가입 흐름을 거치지 않고 만들어진 고객이라는 뜻이다.
                    log.warn("이체한도 행이 없어 정책 기본값으로 응답합니다 - customerId={}", customerId);
                    return TransferLimit.create(customerId);
                });

        LocalDate today = LocalDate.now(clock);
        TransferLimitDailyUsage usage = transferLimitQueryPort.findUsage(customerId, today)
                .orElseGet(() -> TransferLimitDailyUsage.create(customerId, today));

        return TransferLimitResult.from(limit, usage);
    }
}
