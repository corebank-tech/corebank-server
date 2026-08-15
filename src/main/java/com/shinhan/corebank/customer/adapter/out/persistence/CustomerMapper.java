package com.shinhan.corebank.customer.adapter.out.persistence;

import com.shinhan.corebank.customer.domain.model.Customer;
import org.springframework.stereotype.Component;

import java.util.Objects;

// Customer 도메인 모델과 JPA Entity 사이의 변환 담당
@Component
public class CustomerMapper {

    // JPA Entity를 Customer 도메인 모델로 변환
    public Customer toDomain(CustomerJpaEntity entity) {
        Objects.requireNonNull(
                entity,
                "entity must not be null"
        );

        return Customer.restore(
                entity.getCustomerId(),
                entity.getUserId(),
                entity.getPasswordHash(),
                entity.getUserName(),
                entity.getBirthDate(),
                entity.getEmail(),
                entity.getPhoneNumber(),
                entity.getLoginFailureCount(),
                entity.isAccountLocked(),
                entity.getDisplayOrderType(),
                entity.getLastLoginAt(),
                entity.getLastLoginIp(),
                entity.getPreviousLoginAt(),
                entity.getPasswordChangedAt(),
                entity.getJoinedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    // 신규 Customer 도메인 모델을 JPA Entity로 변환
    public CustomerJpaEntity toEntity(Customer customer) {
        Objects.requireNonNull(
                customer,
                "customer must not be null"
        );

        return CustomerJpaEntity.builder()
                .customerId(customer.getCustomerId())
                .userId(customer.getUserId())
                .passwordHash(customer.getPasswordHash())
                .userName(customer.getUserName())
                .birthDate(customer.getBirthDate())
                .email(customer.getEmail())
                .phoneNumber(customer.getPhoneNumber())
                .loginFailureCount(customer.getLoginFailureCount())
                .accountLocked(customer.isAccountLocked())
                .displayOrderType(customer.getDisplayOrderType())
                .lastLoginAt(customer.getLastLoginAt())
                .lastLoginIp(customer.getLastLoginIp())
                .previousLoginAt(customer.getPreviousLoginAt())
                .passwordChangedAt(customer.getPasswordChangedAt())
                .joinedAt(customer.getJoinedAt())
                .build();
    }
}
