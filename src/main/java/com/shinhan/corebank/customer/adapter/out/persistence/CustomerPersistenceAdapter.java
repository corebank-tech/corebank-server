package com.shinhan.corebank.customer.adapter.out.persistence;

import com.shinhan.corebank.customer.application.port.out.CustomerPersistencePort;
import com.shinhan.corebank.customer.domain.model.Customer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

// CustomerPersistencePort를 JPA로 구현하는 영속성 어댑터
@Component
@RequiredArgsConstructor
public class CustomerPersistenceAdapter
        implements CustomerPersistencePort {

    private final CustomerJpaRepository customerJpaRepository;
    private final CustomerMapper customerMapper;

    // 로그인 아이디로 고객을 조회하고 도메인 모델로 변환
    @Override
    public Optional<Customer> findByUserId(String userId) {
        Objects.requireNonNull(
                userId,
                "userId must not be null"
        );

        return customerJpaRepository.findByUserId(userId)
                .map(customerMapper::toDomain);
    }

    // 고객 PK로 고객을 조회하고 도메인 모델로 변환
    @Override
    public Optional<Customer> findById(Long customerId) {
        Objects.requireNonNull(
                customerId,
                "customerId must not be null"
        );

        return customerJpaRepository.findById(customerId)
                .map(customerMapper::toDomain);
    }

    // 로그인 상태 변경을 위해 고객을 비관적 락으로 조회하고 도메인으로 변환
    @Override
    public Optional<Customer> findByIdForUpdate(Long customerId) {
        Objects.requireNonNull(
                customerId,
                "customerId must not be null"
        );

        return customerJpaRepository.findByIdForUpdate(customerId)
                .map(customerMapper::toDomain);
    }

    @Override
    public boolean existsByUserId(String userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        return customerJpaRepository.existsByUserId(userId);
    }

    @Override
    public boolean existsByEmail(String email) {
        Objects.requireNonNull(email, "email must not be null");
        return customerJpaRepository.existsByEmail(email);
    }

    @Override
    public boolean existsByExistingBankCustomerId(
            String existingBankCustomerId
    ) {
        Objects.requireNonNull(
                existingBankCustomerId,
                "existingBankCustomerId must not be null"
        );
        return customerJpaRepository.existsByExistingBankCustomerId(
                existingBankCustomerId
        );
    }

    // 로그인 실패 횟수와 계정 잠금 상태만 저장
    @Override
    public void updateLoginFailureState(Customer customer) {
        Objects.requireNonNull(
                customer,
                "customer must not be null"
        );

        CustomerJpaEntity entity =
                findExistingEntityForUpdate(customer.getCustomerId());

        entity.updateLoginFailureState(
                customer.getLoginFailureCount(),
                customer.isAccountLocked()
        );

        customerJpaRepository.save(entity);
    }

    // 로그인 성공 시각과 접속 IP 및 실패 횟수만 저장
    @Override
    public void updateLoginSuccessState(Customer customer) {
        Objects.requireNonNull(
                customer,
                "customer must not be null"
        );

        CustomerJpaEntity entity =
                findExistingEntityForUpdate(customer.getCustomerId());

        entity.updateLoginSuccessState(
                customer.getPreviousLoginAt(),
                customer.getLastLoginAt(),
                customer.getLastLoginIp(),
                customer.getLoginFailureCount()
        );

        customerJpaRepository.save(entity);
    }

    // 비관적 락으로 조회한 고객의 연락처를 갱신하고 즉시 DB 제약을 확인한다.
    @Override
    public Customer updateContactInfo(Customer customer) {
        Objects.requireNonNull(customer, "customer must not be null");

        CustomerJpaEntity entity =
                findExistingEntityForUpdate(customer.getCustomerId());
        entity.updateContactInfo(
                customer.getPhoneNumber(),
                customer.getEmail()
        );

        CustomerJpaEntity savedEntity =
                customerJpaRepository.saveAndFlush(entity);
        return customerMapper.toDomain(savedEntity);
    }

    // customerId가 없는 신규 고객만 저장
    @Override
    public Customer save(Customer customer) {
        Objects.requireNonNull(
                customer,
                "customer must not be null"
        );

        if (customer.getCustomerId() != null) {
            throw new IllegalArgumentException(
                    "신규 고객은 customerId가 없어야 합니다."
            );
        }

        CustomerJpaEntity newEntity =
                customerMapper.toEntity(customer);

        CustomerJpaEntity savedEntity =
                customerJpaRepository.save(newEntity);

        return customerMapper.toDomain(savedEntity);
    }

    // 상태를 갱신할 기존 고객 Entity를 조회
    private CustomerJpaEntity findExistingEntityForUpdate(Long customerId) {
        Objects.requireNonNull(
                customerId,
                "customerId must not be null"
        );

        return customerJpaRepository.findByIdForUpdate(customerId)
                .orElseThrow(() -> new IllegalStateException(
                        "저장할 고객이 존재하지 않습니다."
                ));
    }
}
