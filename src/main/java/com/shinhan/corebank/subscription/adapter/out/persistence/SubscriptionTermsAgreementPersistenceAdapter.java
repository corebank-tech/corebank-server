package com.shinhan.corebank.subscription.adapter.out.persistence;

import com.shinhan.corebank.subscription.application.port.out.SaveTermsAgreementPort;
import com.shinhan.corebank.subscription.domain.SubscriptionTermsAgreement;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class SubscriptionTermsAgreementPersistenceAdapter implements SaveTermsAgreementPort {

    private final SubscriptionTermsAgreementJpaRepository repository;

    @Override
    public void saveAll(List<SubscriptionTermsAgreement> agreements) {
        List<SubscriptionTermsAgreementJpaEntity> entities = agreements.stream()
                .map(SubscriptionTermsAgreementMapper::toEntity)
                .toList();
        // saveAll()만 쓰면 Hibernate가 INSERT를 트랜잭션 커밋 시점까지 미룰 수 있어, terms_id
        // FK 위반 같은 실패가 이 호출부가 아니라 훨씬 나중(관련 없는 다음 쿼리·커밋 시점)에
        // 터진다 — 실행 서비스는 이 저장이 끝나는 즉시 성공/실패를 알아야 하므로 즉시 flush한다.
        repository.saveAllAndFlush(entities);
    }
}
