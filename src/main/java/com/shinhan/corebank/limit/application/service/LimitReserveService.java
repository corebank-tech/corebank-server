package com.shinhan.corebank.limit.application.service;

import java.time.Clock;
import java.time.LocalDate;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.limit.api.TransferLimitReserver;
import com.shinhan.corebank.limit.application.port.out.TransferLimitCommandPort;
import com.shinhan.corebank.limit.domain.TransferLimit;
import com.shinhan.corebank.limit.domain.TransferLimitDailyUsage;
import com.shinhan.corebank.limit.domain.exception.LmtErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class LimitReserveService implements TransferLimitReserver {

    private final TransferLimitCommandPort transferLimitCommandPort;
    private final Clock clock;

    /**
     * 1회 한도 → 1일 누적 한도 순으로 검사하고 통과하면 당일 사용액에 적립한다.
     *
     * <p>호출자(이체 실행)의 트랜잭션에 참여하므로 여기서 잡은 락은 이체가 끝날 때까지 유지되고,
     * 이체가 실패하면 적립도 함께 롤백된다. 이체 실행은 한도 락을 계좌 락보다 먼저 잡는다
     * (P1-P4 합의) - 여기서 예외가 나면 계좌 락은 아예 시도되지 않는다.
     *
     * <p>검사와 적립을 나눌 수 없다. 한도를 넘는지 먼저 보고 나서 더해야 하므로 그 사이에 다른
     * 이체가 끼어들면 둘 다 통과해 한도를 넘길 수 있다. X-Lock 이 그 구간을 직렬화한다.
     */
    @Override
    public void checkAndReserve(Long customerId, long amount) {
        TransferLimit limit = transferLimitCommandPort.findByCustomerIdForShare(customerId)
                .orElseGet(() -> {
                    // 가입 시 기본값 부여(REQ-TRSF-029)가 회원가입 흐름에 연결되면 이 경로는 데이터 결함이 된다.
                    log.warn("이체한도 행이 없어 정책 기본값으로 검사합니다 - customerId={}", customerId);
                    return TransferLimit.create(customerId);
                });

        if (amount > limit.getOneTimeLimit()) {
            throw new BusinessException(LmtErrorCode.ONE_TIME_LIMIT_EXCEEDED);
        }

        LocalDate today = LocalDate.now(clock);
        TransferLimitDailyUsage usage = transferLimitCommandPort.lockDailyUsage(customerId, today);
        if (usage.getUsedAmount() + amount > limit.getDailyLimit()) {
            throw new BusinessException(LmtErrorCode.DAILY_LIMIT_EXCEEDED);
        }

        usage.add(amount);
        transferLimitCommandPort.saveUsage(usage);
    }
}
