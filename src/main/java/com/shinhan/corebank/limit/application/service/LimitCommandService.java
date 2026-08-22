package com.shinhan.corebank.limit.application.service;

import java.time.Clock;
import java.time.LocalDate;

import com.shinhan.corebank.limit.application.port.in.LimitCommandUseCase;
import com.shinhan.corebank.limit.application.port.in.dto.LimitCommand;
import com.shinhan.corebank.limit.application.port.in.dto.LimitResult;
import com.shinhan.corebank.limit.application.port.out.AuthTokenVerificationPort;
import com.shinhan.corebank.limit.application.port.out.TransferLimitCommandPort;
import com.shinhan.corebank.limit.application.port.out.TransferLimitHistoryPort;
import com.shinhan.corebank.limit.application.port.out.TransferLimitQueryPort;
import com.shinhan.corebank.limit.domain.TransferLimit;
import com.shinhan.corebank.limit.domain.TransferLimitDailyUsage;
import com.shinhan.corebank.limit.domain.TransferLimitHistory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class LimitCommandService implements LimitCommandUseCase {

    private final TransferLimitCommandPort transferLimitCommandPort;
    private final TransferLimitQueryPort transferLimitQueryPort;
    private final TransferLimitHistoryPort transferLimitHistoryPort;
    private final AuthTokenVerificationPort authTokenVerificationPort;
    private final Clock clock;

    /**
     * 고객 본인의 1회·1일 이체한도를 함께 교체한다(REQ-TRSF-025).
     *
     * <p>읽고-검사하고-쓰는 사이에 다른 변경이 끼어들지 못하도록 X-Lock 으로 읽는다. 이체 실행
     * 경로는 같은 행을 S-Lock 으로 읽으므로, 이체가 진행 중이면 이 트랜잭션이 대기한다.
     *
     * <p>변경 직전 값을 같은 트랜잭션에서 이력으로 남긴다. 변경 후 값은 다음 이력의 변경 전
     * 값이고 마지막 이력의 변경 후 값은 transfer_limit 의 현재값이라 저장하지 않는다.
     */
    @Override
    public LimitResult update(Long customerId, LimitCommand command) {
        // 소모 없는 검증을 먼저 끝낸다. 뒤의 verifyAndConsumeOtp 가 토큰을 소비하므로, 여기서
        // 걸러야 할 입력 실수를 그 뒤에 두면 OTP 를 헛되이 쓰게 된다.
        TransferLimit.validateOrder(command.oneTimeLimit(), command.dailyLimit());

        authTokenVerificationPort.verifyAccountPassword(command.accountPasswordAuthToken(), customerId);
        authTokenVerificationPort.verifyAndConsumeOtp(
                command.otpAuthToken(), customerId, command.oneTimeLimit(), command.dailyLimit());

        TransferLimit limit = transferLimitCommandPort.findForUpdateByCustomerId(customerId)
                .orElseGet(() -> {
                    // 가입 시 기본값 부여(REQ-TRSF-029)가 회원가입 흐름에 연결되면 이 경로는 데이터 결함이 된다.
                    log.warn("이체한도 행이 없어 정책 기본값에서 변경합니다 - customerId={}", customerId);
                    return TransferLimit.create(customerId);
                });

        transferLimitHistoryPort.save(
                TransferLimitHistory.create(customerId, limit.getOneTimeLimit(), limit.getDailyLimit()));

        limit.update(command.oneTimeLimit(), command.dailyLimit());
        TransferLimit saved = transferLimitCommandPort.save(limit);

        LocalDate today = LocalDate.now(clock);
        TransferLimitDailyUsage usage = transferLimitQueryPort.findUsage(customerId, today)
                .orElseGet(() -> TransferLimitDailyUsage.create(customerId, today));

        return LimitResult.from(saved, usage);
    }
}
