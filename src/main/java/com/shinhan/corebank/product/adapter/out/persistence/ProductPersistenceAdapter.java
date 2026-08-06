package com.shinhan.corebank.product.adapter.out.persistence;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.shinhan.corebank.product.application.ProductSortType;
import com.shinhan.corebank.product.application.port.out.ProductQueryPort;
import com.shinhan.corebank.product.domain.Product;
import com.shinhan.corebank.product.domain.ProductGroup;
import com.shinhan.corebank.product.domain.SaleStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.shinhan.corebank.product.adapter.out.persistence.QProductJpaEntity.productJpaEntity;

@Repository
@RequiredArgsConstructor
public class ProductPersistenceAdapter implements ProductQueryPort {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Product> search(ProductGroup productGroup, String keyword, ProductSortType sort, Pageable pageable) {
        Predicate[] conditions = conditions(productGroup, keyword);

        List<ProductJpaEntity> content = queryFactory
                .selectFrom(productJpaEntity)
                .where(conditions)
                .orderBy(orderSpecifier(sort))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(productJpaEntity.count())
                .from(productJpaEntity)
                .where(conditions)
                .fetchOne();

        List<Product> domainContent = content.stream().map(ProductMapper::toDomain).toList();
        return new PageImpl<>(domainContent, pageable, total == null ? 0 : total);
    }

    private Predicate[] conditions(ProductGroup productGroup, String keyword) {
        return new Predicate[] {
                productJpaEntity.saleStatus.eq(SaleStatus.ON_SALE),
                productGroupEq(productGroup),
                keywordContains(keyword)
        };
    }

    private BooleanExpression productGroupEq(ProductGroup productGroup) {
        return productGroup != null ? productJpaEntity.productGroup.eq(productGroup) : null;
    }

    private BooleanExpression keywordContains(String keyword) {
        return (keyword != null && !keyword.isBlank()) ? productJpaEntity.productName.contains(keyword) : null;
    }

    private OrderSpecifier<?> orderSpecifier(ProductSortType sort) {
        return switch (sort) {
            case NEW -> productJpaEntity.saleStartDate.desc();
            case NAME -> productJpaEntity.productName.asc();
            case RATE -> productJpaEntity.maxRate.desc();
        };
    }
}
