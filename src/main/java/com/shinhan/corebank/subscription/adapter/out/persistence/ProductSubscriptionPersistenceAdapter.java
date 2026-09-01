package com.shinhan.corebank.subscription.adapter.out.persistence;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.subscription.application.SubscriptionErrorCode;
import com.shinhan.corebank.subscription.application.port.out.ExistingSubscriptionPort;
import com.shinhan.corebank.subscription.application.port.out.ProductSubscriptionQueryPort;
import com.shinhan.corebank.subscription.application.port.out.SaveProductSubscriptionPort;
import com.shinhan.corebank.subscription.domain.ProductSubscription;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class ProductSubscriptionPersistenceAdapter
        implements ProductSubscriptionQueryPort, SaveProductSubscriptionPort, ExistingSubscriptionPort {
    private final ProductSubscriptionJpaRepository productSubscriptionJpaRepository;
    private final ProductLockJpaRepository productLockJpaRepository;

    @Override
    public Optional<ProductSubscription> findByIdAndCustomerId(Long subscriptionId, Long customerId) {
        return productSubscriptionJpaRepository
                .findBySubscriptionIdAndCustomerId(subscriptionId, customerId)
                .map(ProductSubscriptionMapper::toDomain);
    }

    @Override
    public ProductSubscription save(ProductSubscription subscription) {
        ProductSubscriptionJpaEntity saved =
                productSubscriptionJpaRepository.save(ProductSubscriptionMapper.toEntity(subscription));
        return ProductSubscriptionMapper.toDomain(saved);
    }

    // MANDATORY — 트랜잭션 밖에서 부르면 락이 리포지토리 메서드 종료와 함께 즉시 풀려 보호 효과가
    // 없다. 그 상태를 조용히 통과시키지 않고 호출 시점에 실패시킨다.
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean existsActiveSubscription(Long customerId, Long productId) {
        // 1) product 행에 먼저 락을 걸어 같은 productId로 동시에 들어온 다른 트랜잭션을 이 트랜잭션이
        //    끝날 때까지 대기시킨다(순서 직렬화). 행이 없으면 락이 걸리지 않은 채 통과하므로
        //    (= 동시성 보호가 조용히 사라지므로) 반환값을 버리지 않고 여기서 끊는다.
        // 2) 뒤이은 조회도 반드시 잠금 조회로 한다 — 평범한 SELECT는 REPEATABLE READ 스냅샷에 묶여
        //    상대가 그 사이 커밋한 행을 못 본다(ProductSubscriptionJpaRepository 주석 참고).
        productLockJpaRepository
                .findByIdForUpdate(productId)
                .orElseThrow(() -> new BusinessException(SubscriptionErrorCode.PRODUCT_LOCK_TARGET_NOT_FOUND));
        return productSubscriptionJpaRepository.findAllByCustomerIdAndProductIdForUpdate(customerId, productId).stream()
                .anyMatch(entity -> entity.getStatus() == ProcessResultStatus.SUCCESS);
    }
}
