package com.shinhan.corebank.limit.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.limit.api.TransferLimitProvider;
import com.shinhan.corebank.limit.application.port.out.TransferLimitQueryPort;
import com.shinhan.corebank.limit.domain.TransferLimit;
import com.shinhan.corebank.limit.domain.exception.LmtErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * limit/api 의 조회 계약만 맡는다. 웹 조회(TransferLimitQueryUseCase)와 한 클래스에 두지 않는 것은,
 * 스프링의 빈 교체가 타입이 아니라 이름 단위이기 때문이다 - 겸업하면 autotransfer·
 * scheduledtransfer 가 이 계약을 mock 으로 바꾸는 순간 limit 의 웹 조회 주입처까지 깨진다.
 */
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
        return transferLimitQueryPort
                .findByCustomerId(customerId)
                .map(TransferLimit::getOneTimeLimit)
                // 가입 연계(REQ-TRSF-029)와 백필이 모든 고객의 한도 행을 보장한다.
                // 그래도 없다면 사용자 잘못이 아니라 데이터 결함이므로 기본값으로 덮지 않고 드러낸다.
                .orElseThrow(() -> new BusinessException(LmtErrorCode.TRANSFER_LIMIT_NOT_FOUND));
    }
}
