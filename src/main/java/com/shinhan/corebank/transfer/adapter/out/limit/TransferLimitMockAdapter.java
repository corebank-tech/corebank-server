package com.shinhan.corebank.transfer.adapter.out.limit;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.transfer.application.port.out.TransferLimitPort;
import com.shinhan.corebank.transfer.domain.exception.LimitErrorCode;

import org.springframework.stereotype.Component;

/**
 * P1의 실제 한도 도메인(1일 누적 사용량 테이블)이 이 리포에 아직 없어(4주차 예정), 1회 한도만
 * 고정 상한으로 검사한다. 1일 한도(LMT0003)는 누적 사용량을 추적할 데이터가 없어 이 Mock에서는
 * 검사하지 않는다 — 실 도메인이 들어오면 이 클래스 전체가 교체된다.
 * autotransfer.adapter.out.limit.MockTransferLimitPort와 이름이 같으면 스프링 빈 이름이
 * 단순 클래스명 기준이라 패키지가 달라도 충돌하므로, 이 모듈의 *Adapter 네이밍 컨벤션으로
 * 구분한다.
 * 이 포트의 다른 구현체가 없어 @Profile로 좁히면 prod 기동이 실패한다 - P1 실구현 전까지 모든 프로필에서 활성화한다.
 */
@Component
public class TransferLimitMockAdapter implements TransferLimitPort {

    private static final long ONE_TIME_LIMIT = 10_000_000L;

    @Override
    public void checkAndReserve(Long customerId, long amount) {
        if (amount > ONE_TIME_LIMIT) {
            throw new BusinessException(LimitErrorCode.ONE_TIME_LIMIT_EXCEEDED);
        }
    }
}
