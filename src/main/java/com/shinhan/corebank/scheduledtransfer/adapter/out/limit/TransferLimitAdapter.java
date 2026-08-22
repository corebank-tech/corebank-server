package com.shinhan.corebank.scheduledtransfer.adapter.out.limit;

import com.shinhan.corebank.limit.api.TransferLimitProvider;
import com.shinhan.corebank.scheduledtransfer.application.port.out.TransferLimitPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 빈 이름 명시: autotransfer.adapter.out.limit.TransferLimitAdapter와 클래스 단순이름이 같아
// 기본 빈 이름(transferLimitAdapter)이 충돌한다.
// 다른 도메인(limit) 소유 테이블을 읽기 전용으로 볼 때는 자체 JPA 매핑 대신 소유 도메인의 공개
// 계약을 경유한다(ADR-0002, team_db_architecture_guide.md §3-①).
// 등록 검증에는 1회 한도만 필요하므로 당일 사용액까지 함께 읽는 LimitQueryUseCase.get() 대신
// 1회 한도 전용 계약을 쓴다 - 등록 요청마다 나가던 사용액 조회 한 건이 없어진다.
@Component("scheduledTransferTransferLimitAdapter")
@RequiredArgsConstructor
public class TransferLimitAdapter implements TransferLimitPort {

    private final TransferLimitProvider transferLimitProvider;

    @Override
    public long findOneTimeLimit(Long customerId) {
        return transferLimitProvider.findOneTimeLimit(customerId);
    }
}
