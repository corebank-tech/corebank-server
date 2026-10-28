package com.shinhan.corebank.customer.adapter.out.persistence;

import com.shinhan.corebank.customer.domain.model.Customer;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// Customer 도메인 모델과 JPA Entity 사이의 변환 담당
@Component
@RequiredArgsConstructor
public class CustomerMapper {

    private final CustomerPiiReadSupport piiReadSupport;

    public Customer toDomain(CustomerJpaEntity entity) {
        Objects.requireNonNull(entity, "entity must not be null");

        String storedEmail = piiReadSupport.encryptedReads() ? entity.getEmailCiphertext() : entity.getEmail();

        String storedPhoneNumber =
                piiReadSupport.encryptedReads() ? entity.getPhoneNumberCiphertext() : entity.getPhoneNumber();

        String email = piiReadSupport.email(storedEmail);
        String phoneNumber = piiReadSupport.phoneNumber(storedPhoneNumber);

        return Customer.restore(
                entity.getCustomerId(),
                entity.getUserId(),
                entity.getExistingBankCustomerId(),
                entity.getPasswordHash(),
                entity.getUserName(),
                entity.getBirthDate(),
                email,
                phoneNumber,
                entity.getLoginFailureCount(),
                entity.isAccountLocked(),
                entity.getStatus(),
                entity.getRole(),
                entity.getLastLoginAt(),
                entity.getLastLoginIp(),
                entity.getPreviousLoginAt(),
                entity.getPasswordChangedAt(),
                entity.getJoinedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    public CustomerJpaEntity toEntity(Customer customer) {
        Objects.requireNonNull(customer, "customer must not be null");

        return CustomerJpaEntity.builder()
                .customerId(customer.getCustomerId())
                .userId(customer.getUserId())
                .existingBankCustomerId(customer.getExistingBankCustomerId())
                .passwordHash(customer.getPasswordHash())
                .userName(customer.getUserName())
                .birthDate(customer.getBirthDate())
                .email(customer.getEmail())
                .phoneNumber(customer.getPhoneNumber())
                .loginFailureCount(customer.getLoginFailureCount())
                .accountLocked(customer.isAccountLocked())
                .status(customer.getStatus())
                .role(customer.getRole())
                .lastLoginAt(customer.getLastLoginAt())
                .lastLoginIp(customer.getLastLoginIp())
                .previousLoginAt(customer.getPreviousLoginAt())
                .passwordChangedAt(customer.getPasswordChangedAt())
                .joinedAt(customer.getJoinedAt())
                .build();
    }
}
