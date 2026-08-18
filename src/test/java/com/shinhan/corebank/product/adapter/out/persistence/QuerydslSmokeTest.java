package com.shinhan.corebank.product.adapter.out.persistence;

import static com.shinhan.corebank.product.adapter.out.persistence.QProductJpaEntity.productJpaEntity;
import static org.assertj.core.api.Assertions.assertThat;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.product.domain.ProductGroup;
import com.shinhan.corebank.product.domain.SaleStatus;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

// Querydsl 인프라(#31) 도입 확인용 스모크 테스트. 도메인 코드에 실제 동적 쿼리가 생기면 지워도 된다.
@Transactional
class QuerydslSmokeTest extends IntegrationTestSupport {

    @Autowired
    ProductJpaRepository repository;

    @Autowired
    JPAQueryFactory queryFactory;

    @Autowired
    EntityManager entityManager;

    @Test
    @DisplayName("Q타입으로 조립한 동적 조건이 실제 쿼리로 실행된다")
    void dynamicPredicateQueryWorks() {
        ProductJpaEntity saved = repository.save(ProductTestFixtures.defaultProduct());
        entityManager.flush();
        entityManager.clear();

        List<ProductJpaEntity> result = queryFactory
                .selectFrom(productJpaEntity)
                .where(
                        productJpaEntity.productGroup.eq(ProductGroup.DEPOSIT),
                        productJpaEntity.saleStatus.eq(SaleStatus.ON_SALE)
                )
                .fetch();

        assertThat(result)
                .extracting(ProductJpaEntity::getProductId)
                .contains(saved.getProductId());
    }
}
