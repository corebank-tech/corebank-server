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

    // 신규 고객을 저장하거나 기존 고객의 변경 상태를 반영
    @Override
    public Customer save(Customer customer) {
        Objects.requireNonNull(
                customer,
                "customer must not be null"
        );

        if (customer.getCustomerId() == null) {
            CustomerJpaEntity newEntity =
                    customerMapper.toEntity(customer);

            CustomerJpaEntity savedEntity =
                    customerJpaRepository.save(newEntity);

            return customerMapper.toDomain(savedEntity);
        }

        CustomerJpaEntity existingEntity =
                customerJpaRepository.findById(customer.getCustomerId())
                        .orElseThrow(() -> new IllegalStateException(
                                "저장할 고객이 존재하지 않습니다."
                        ));

        customerMapper.updateEntity(customer, existingEntity);

        CustomerJpaEntity savedEntity =
                customerJpaRepository.save(existingEntity);

        return customerMapper.toDomain(savedEntity);
    }
}
