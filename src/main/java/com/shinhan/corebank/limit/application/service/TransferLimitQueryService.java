package com.shinhan.corebank.limit.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.limit.application.port.in.TransferLimitQueryUseCase;
import com.shinhan.corebank.limit.application.port.in.dto.TransferLimitResult;
import com.shinhan.corebank.limit.application.port.out.TransferLimitQueryPort;
import com.shinhan.corebank.limit.domain.TransferLimit;
import com.shinhan.corebank.limit.domain.TransferLimitDailyUsage;
import com.shinhan.corebank.limit.domain.exception.LmtErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransferLimitQueryService implements TransferLimitQueryUseCase {

    private final TransferLimitQueryPort transferLimitQueryPort;
    private final Clock clock;

    /**
     * 한도 행이 없으면 LMT9001 로 거부한다. 가입 연계(REQ-TRSF-029)와 백필이 모든 고객의
     * 행을 보장하므로, 없다는 건 사용자 잘못이 아니라 데이터 결함이다. 기본값으로 덮으면
     * 그 결함이 정상 응답에 묻혀 드러나지 않는다.
     * 조회는 읽기 전용이라 이 시점에 행을 만들지 않는다.
     * 당일 사용액 행은 그날 첫 이체 전까지 없으므로 사용액 0으로 본다.
     */
    @Override
    public TransferLimitResult get(Long customerId) {
        TransferLimit limit = transferLimitQueryPort
                .findByCustomerId(customerId)
                // 가입 연계(REQ-TRSF-029)와 백필이 모든 고객의 한도 행을 보장한다.
                // 그래도 없다면 사용자 잘못이 아니라 데이터 결함이므로 기본값으로 덮지 않고 드러낸다.
                .orElseThrow(() -> new BusinessException(LmtErrorCode.TRANSFER_LIMIT_NOT_FOUND));

        LocalDate today = LocalDate.now(clock);
        TransferLimitDailyUsage usage = transferLimitQueryPort
                .findUsage(customerId, today)
                .orElseGet(() -> TransferLimitDailyUsage.create(customerId, today));

        return TransferLimitResult.from(limit, usage);
    }
}
