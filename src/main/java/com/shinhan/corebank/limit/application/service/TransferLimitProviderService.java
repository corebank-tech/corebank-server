package com.shinhan.corebank.limit.application.service;

import com.shinhan.corebank.limit.api.TransferLimitProvider;
import com.shinhan.corebank.limit.application.port.out.TransferLimitQueryPort;
import com.shinhan.corebank.limit.domain.TransferLimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * limit/api 의 조회 계약만 맡는다. 웹 조회(LimitQueryUseCase)와 한 클래스에 두지 않는 것은,
 * 스프링의 빈 교체가 타입이 아니라 이름 단위이기 때문이다 - 겸업하면 autotransfer·
 * scheduledtransfer 가 이 계약을 mock 으로 바꾸는 순간 limit 의 웹 조회 주입처까지 깨진다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransferLimitProviderService implements TransferLimitProvider {

    private final TransferLimitQueryPort transferLimitQueryPort;

    /**
     * 자동이체·예약이체 등록 시점 검증용이라 락을 걸지 않는다. 등록은 돈을 옮기지 않고, 실제
     * 이체가 실행될 때 checkAndReserve 가 락을 잡고 다시 검사한다.
     */
    @Override
    public long findOneTimeLimit(Long customerId) {
        return transferLimitQueryPort.findByCustomerId(customerId)
                .map(TransferLimit::getOneTimeLimit)
                .orElseGet(() -> {
                    // 가입 연계(REQ-TRSF-029)가 붙은 뒤로 이 로그는 데이터 결함 신호다.
                    // 가입 흐름을 거치지 않고 만들어진 고객이라는 뜻이다.
                    log.warn("이체한도 행이 없어 정책 기본값으로 응답합니다 - customerId={}", customerId);
                    return TransferLimit.DEFAULT_ONE_TIME_LIMIT;
                });
    }
}
