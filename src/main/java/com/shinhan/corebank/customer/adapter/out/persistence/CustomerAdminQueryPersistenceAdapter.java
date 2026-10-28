package com.shinhan.corebank.customer.adapter.out.persistence;

import static com.shinhan.corebank.customer.adapter.out.persistence.QCustomerJpaEntity.customerJpaEntity;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.StringPath;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.shinhan.corebank.customer.application.port.out.CustomerAdminQueryPort;
import com.shinhan.corebank.customer.application.port.out.CustomerAdminView;
import com.shinhan.corebank.customer.application.port.out.CustomerSearchCondition;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

@Repository
public class CustomerAdminQueryPersistenceAdapter implements CustomerAdminQueryPort {

    private final JPAQueryFactory queryFactory;
    private final CustomerPiiReadSupport piiReadSupport;

    public CustomerAdminQueryPersistenceAdapter(JPAQueryFactory queryFactory, CustomerPiiReadSupport piiReadSupport) {
        this.queryFactory = queryFactory;
        this.piiReadSupport = piiReadSupport;
    }

    @Override
    public Page<CustomerAdminView> search(CustomerSearchCondition condition, Pageable pageable) {

        Predicate[] predicates = conditions(condition);

        JPAQuery<Tuple> query = selectView()
                .where(predicates)
                .orderBy(customerJpaEntity.joinedAt.desc(), customerJpaEntity.customerId.desc());

        if (pageable.isPaged()) {
            query.offset(pageable.getOffset()).limit(pageable.getPageSize());
        }

        List<CustomerAdminView> content =
                query.fetch().stream().map(this::toView).toList();

        return PageableExecutionUtils.getPage(content, pageable, () -> count(condition));
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

        Tuple row =
                selectView().where(customerJpaEntity.customerId.eq(customerId)).fetchOne();

        return Optional.ofNullable(row).map(this::toView);
    }

    // 암호화 조회 모드에서는 평문 컬럼 대신 암호문 컬럼만 선택한다.
    private StringPath emailPath() {
        return piiReadSupport.encryptedReads() ? customerJpaEntity.emailCiphertext : customerJpaEntity.email;
    }

    private StringPath phoneNumberPath() {
        return piiReadSupport.encryptedReads()
                ? customerJpaEntity.phoneNumberCiphertext
                : customerJpaEntity.phoneNumber;
    }

    private JPAQuery<Tuple> selectView() {
        return queryFactory
                .select(
                        customerJpaEntity.customerId,
                        customerJpaEntity.userId,
                        customerJpaEntity.userName,
                        customerJpaEntity.birthDate,
                        emailPath(),
                        phoneNumberPath(),
                        customerJpaEntity.loginFailureCount,
                        customerJpaEntity.accountLocked,
                        customerJpaEntity.status,
                        customerJpaEntity.lastLoginAt,
                        customerJpaEntity.joinedAt)
                .from(customerJpaEntity);
    }

    private CustomerAdminView toView(Tuple row) {
        String email = piiReadSupport.email(row.get(emailPath()));
        String phoneNumber = piiReadSupport.phoneNumber(row.get(phoneNumberPath()));

        return new CustomerAdminView(
                row.get(customerJpaEntity.customerId),
                row.get(customerJpaEntity.userId),
                row.get(customerJpaEntity.userName),
                row.get(customerJpaEntity.birthDate),
                email,
                phoneNumber,
                row.get(customerJpaEntity.loginFailureCount),
                row.get(customerJpaEntity.accountLocked),
                row.get(customerJpaEntity.status),
                row.get(customerJpaEntity.lastLoginAt),
                row.get(customerJpaEntity.joinedAt));
    }

    private Predicate[] conditions(CustomerSearchCondition condition) {
        Objects.requireNonNull(condition, "condition must not be null");

        return new Predicate[] {
            condition.userId() != null ? customerJpaEntity.userId.startsWith(condition.userId()) : null,
            condition.userName() != null ? customerJpaEntity.userName.startsWith(condition.userName()) : null,
            emailEq(condition.email()),
            accountLockedEq(condition.accountLocked()),
            condition.status() != null ? customerJpaEntity.status.eq(condition.status()) : null
        };
    }

    private BooleanExpression emailEq(String email) {
        if (email == null) {
            return null;
        }

        if (piiReadSupport.encryptedReads()) {
            return customerJpaEntity.emailSearchHash.eq(piiReadSupport.emailHash(email));
        }

        return customerJpaEntity.email.eq(email);
    }

    private BooleanExpression accountLockedEq(Boolean accountLocked) {
        return accountLocked != null ? customerJpaEntity.accountLocked.eq(accountLocked) : null;
    }
}
