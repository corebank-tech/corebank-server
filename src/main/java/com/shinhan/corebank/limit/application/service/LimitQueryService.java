package com.shinhan.corebank.limit.application.service;

import com.shinhan.corebank.limit.application.port.in.LimitQueryUseCase;
import com.shinhan.corebank.limit.application.port.in.dto.LimitResult;
import com.shinhan.corebank.limit.application.port.out.TransferLimitQueryPort;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LimitQueryService implements LimitQueryUseCase {

    private final TransferLimitQueryPort transferLimitQueryPort;

    @Override
    public LimitResult get(Long customerId) {
        throw new UnsupportedOperationException("한도 조회 API 구현 시 채운다");
    }
}
