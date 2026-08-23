package com.shinhan.corebank.autotransfer.adapter.out.limit;

import com.shinhan.corebank.autotransfer.application.port.out.TransferLimitPort;
import com.shinhan.corebank.limit.application.port.in.TransferLimitQueryUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 다른 도메인(limit) 소유 테이블을 읽기 전용으로 볼 때는 자체 JPA 매핑 대신 소유 도메인의 공개
// UseCase를 경유한다(ADR-0002, team_db_architecture_guide.md §3-①).
@Component
@RequiredArgsConstructor
public class TransferLimitAdapter implements TransferLimitPort {

    private final TransferLimitQueryUseCase limitQueryUseCase;

    @Override
    public long findOneTimeLimit(Long customerId) {
        return limitQueryUseCase.get(customerId).oneTimeLimit();
    }
}
