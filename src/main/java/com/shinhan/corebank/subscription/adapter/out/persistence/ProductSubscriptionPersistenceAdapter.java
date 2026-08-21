package com.shinhan.corebank.subscription.adapter.out.persistence;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.subscription.application.port.out.ExistingSubscriptionPort;
import com.shinhan.corebank.subscription.application.port.out.ProductSubscriptionQueryPort;
import com.shinhan.corebank.subscription.application.port.out.SaveProductSubscriptionPort;
import com.shinhan.corebank.subscription.domain.ProductSubscription;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ProductSubscriptionPersistenceAdapter
        implements ProductSubscriptionQueryPort, SaveProductSubscriptionPort, ExistingSubscriptionPort {
    private final ProductSubscriptionJpaRepository productSubscriptionJpaRepository;
    private final ProductLockJpaRepository productLockJpaRepository;

    @Override
    public Optional<ProductSubscription> findByIdAndCustomerId(Long subscriptionId, Long customerId) {
        return productSubscriptionJpaRepository.findBySubscriptionIdAndCustomerId(subscriptionId, customerId)
                .map(ProductSubscriptionMapper::toDomain);
    }

    @Override
    public ProductSubscription save(ProductSubscription subscription) {
        ProductSubscriptionJpaEntity saved =
                productSubscriptionJpaRepository.save(ProductSubscriptionMapper.toEntity(subscription));
        return ProductSubscriptionMapper.toDomain(saved);
    }

    @Override
    public boolean existsActiveSubscription(Long customerId, Long productId) {
        // 1) product 행에 먼저 락을 걸어 같은 productId로 동시에 들어온 다른 트랜잭션을 이 트랜잭션이
        //    끝날 때까지 대기시킨다(순서 직렬화).
        // 2) 뒤이은 조회도 반드시 잠금 조회로 한다 — 평범한 SELECT는 REPEATABLE READ 스냅샷에 묶여
        //    상대가 그 사이 커밋한 행을 못 본다(ProductSubscriptionJpaRepository 주석 참고).
        productLockJpaRepository.findByIdForUpdate(productId);
        return productSubscriptionJpaRepository
                .findAllByCustomerIdAndProductIdForUpdate(customerId, productId)
                .stream()
                .anyMatch(entity -> entity.getStatus() == ProcessResultStatus.SUCCESS);
    }
}
