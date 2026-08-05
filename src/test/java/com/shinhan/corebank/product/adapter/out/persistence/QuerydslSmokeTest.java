package com.shinhan.corebank.product.adapter.out.persistence;

import static com.shinhan.corebank.product.domain.QProduct.product;
import static org.assertj.core.api.Assertions.assertThat;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.product.domain.Product;
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
    @DisplayName("Q타입으로 조립한 동적 조건(BooleanBuilder)이 실제 쿼리로 실행된다")
    void dynamicPredicateQueryWorks() {
        Product saved = repository.save(ProductTestFixtures.defaultProduct());
        entityManager.flush();
        entityManager.clear();

        BooleanBuilder condition = new BooleanBuilder();
        condition.and(product.productGroup.eq(ProductGroup.DEPOSIT));
        condition.and(product.saleStatus.eq(SaleStatus.ON_SALE));

        List<Product> result = queryFactory
                .selectFrom(product)
                .where(condition)
                .fetch();

        assertThat(result)
                .extracting(Product::getProductId)
                .contains(saved.getProductId());
    }
}
