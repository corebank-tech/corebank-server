package com.shinhan.corebank.customer.adapter.out.persistence;

import static com.shinhan.corebank.customer.adapter.out.persistence.QCustomerJpaEntity.customerJpaEntity;

import com.querydsl.core.types.ConstructorExpression;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.shinhan.corebank.customer.application.port.out.CustomerAdminQueryPort;
import com.shinhan.corebank.customer.application.port.out.CustomerAdminView;
import com.shinhan.corebank.customer.application.port.out.CustomerSearchCondition;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

// startsWith는 Querydsl이 %·_·!를 이스케이프해 "like ? escape '!'"로 보낸다 — 입력 와일드카드가 글자로 비교된다.
@Repository
public class CustomerAdminQueryPersistenceAdapter implements CustomerAdminQueryPort {

    private static final ConstructorExpression<CustomerAdminView> VIEW = Projections.constructor(
            CustomerAdminView.class,
            customerJpaEntity.customerId,
            customerJpaEntity.userId,
            customerJpaEntity.userName,
            customerJpaEntity.birthDate,
            customerJpaEntity.email,
            customerJpaEntity.phoneNumber,
            customerJpaEntity.loginFailureCount,
            customerJpaEntity.accountLocked,
            customerJpaEntity.lastLoginAt,
            customerJpaEntity.joinedAt);

    private final JPAQueryFactory queryFactory;

    public CustomerAdminQueryPersistenceAdapter(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    @Override
    public Page<CustomerAdminView> search(CustomerSearchCondition condition, Pageable pageable) {
        Predicate[] conditions = conditions(condition);
        var query = queryFactory
                .select(VIEW)
                .from(customerJpaEntity)
                .where(conditions)
                .orderBy(customerJpaEntity.joinedAt.desc(), customerJpaEntity.customerId.desc());
        if (pageable.isPaged()) {
            query.offset(pageable.getOffset()).limit(pageable.getPageSize());
        }
        List<CustomerAdminView> content = query.fetch();
        return new PageImpl<>(content, pageable, count(condition));
    }

    @Override
    public long count(CustomerSearchCondition condition) {
        Long total = queryFactory
                .select(customerJpaEntity.count())
                .from(customerJpaEntity)
                .where(conditions(condition))
                .fetchOne();
        return total == null ? 0L : total;
    }

    @Override
    public Optional<CustomerAdminView> findById(Long customerId) {
        Objects.requireNonNull(customerId, "customerId must not be null");
        return Optional.ofNullable(queryFactory
                .select(VIEW)
                .from(customerJpaEntity)
                .where(customerJpaEntity.customerId.eq(customerId))
                .fetchOne());
    }

    private Predicate[] conditions(CustomerSearchCondition condition) {
        Objects.requireNonNull(condition, "condition must not be null");
        return new Predicate[] {
            condition.userId() != null ? customerJpaEntity.userId.startsWith(condition.userId()) : null,
            condition.userName() != null ? customerJpaEntity.userName.startsWith(condition.userName()) : null,
            condition.email() != null ? customerJpaEntity.email.eq(condition.email()) : null,
            accountLockedEq(condition.accountLocked())
        };
    }

    private BooleanExpression accountLockedEq(Boolean accountLocked) {
        return accountLocked != null ? customerJpaEntity.accountLocked.eq(accountLocked) : null;
    }
}
