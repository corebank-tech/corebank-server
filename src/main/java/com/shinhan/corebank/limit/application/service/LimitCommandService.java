package com.shinhan.corebank.limit.application.service;

import com.shinhan.corebank.limit.api.TransferLimitRegistration;
import com.shinhan.corebank.limit.application.port.in.LimitCommandUseCase;
import com.shinhan.corebank.limit.application.port.in.dto.LimitCommand;
import com.shinhan.corebank.limit.application.port.out.TransferLimitCommandPort;
import com.shinhan.corebank.limit.domain.TransferLimit;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class LimitCommandService implements LimitCommandUseCase, TransferLimitRegistration {

    private final TransferLimitCommandPort transferLimitCommandPort;

    /**
     * 신규 가입 고객에게 정책 기본값을 부여한다(REQ-TRSF-029).
     *
     * <p>기본 전파(REQUIRED)라 회원가입 트랜잭션에 참여한다. 이미 있는지 먼저 확인하지 않는다 -
     * customerId 가 가입 트랜잭션에서 막 채번된 값이라 같은 고객으로 두 번 들어올 경로가 없고,
     * PK 제약이 단독으로 판정한다.
     */
    @Override
    public void registerDefault(Long customerId) {
        transferLimitCommandPort.save(TransferLimit.create(customerId));
    }

    @Override
    public void update(Long customerId, LimitCommand command) {
        throw new UnsupportedOperationException("한도 변경 API 구현 시 채운다");
    }
}
