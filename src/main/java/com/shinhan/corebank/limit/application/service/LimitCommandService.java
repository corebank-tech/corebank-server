package com.shinhan.corebank.limit.application.service;

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
public class LimitCommandService implements LimitCommandUseCase {

    private final TransferLimitCommandPort transferLimitCommandPort;

    /**
     * 신규 가입 고객에게 정책 기본값을 부여한다. 이미 한도가 있으면 조용히 지나가
     * 가입 재시도가 깨지지 않게 한다.
     */
    @Override
    public void create(Long customerId) {
        if (transferLimitCommandPort.findByCustomerId(customerId).isPresent()) {
            return;
        }
        transferLimitCommandPort.save(TransferLimit.create(customerId));
    }

    @Override
    public void update(Long customerId, LimitCommand command) {
        throw new UnsupportedOperationException("한도 변경 API 구현 시 채운다");
    }
}
