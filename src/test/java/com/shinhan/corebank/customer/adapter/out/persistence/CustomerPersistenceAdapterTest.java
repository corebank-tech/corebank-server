package com.shinhan.corebank.customer.adapter.out.persistence;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.customer.application.port.out.CustomerPersistencePort;
import com.shinhan.corebank.customer.domain.model.Customer;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
@DisplayName("고객 영속성 어댑터 MySQL 통합 테스트")
class CustomerPersistenceAdapterTest extends IntegrationTestSupport {

    private static final String PASSWORD_HASH =
            "$2a$10$34abEWY4uXLwTEnT5hNow.603a5rWofFx7Bnj59agU.PsESK0v/Yq";

    @Autowired
    private CustomerPersistencePort customerPersistencePort;

    @Autowired
    private EntityManager entityManager;

    // customer 전체 컬럼을 MySQL에 저장하고 다시 조회
    @Test
    @DisplayName("Customer를 저장하면 전체 컬럼이 정상적으로 매핑된다")
    void saveAndFindCustomer() {
        LocalDateTime lastLoginAt =
                LocalDateTime.of(2026, 8, 10, 9, 0);

        LocalDateTime previousLoginAt =
                LocalDateTime.of(2026, 8, 9, 9, 0);

        LocalDateTime passwordChangedAt =
                LocalDateTime.of(2026, 7, 1, 10, 0);

        LocalDateTime joinedAt =
                LocalDateTime.of(2026, 1, 1, 9, 0);

        Customer customer = Customer.restore(
                null,
                "login-test-user",
                null,
                PASSWORD_HASH,
                "홍길동",
                LocalDate.of(1990, 1, 1),
                "login-test@example.com",
                "01012345678",
                3,
                false,
                lastLoginAt,
                "127.0.0.1",
                previousLoginAt,
                passwordChangedAt,
                joinedAt,
                null,
                null
        );

        Customer savedCustomer =
                customerPersistencePort.save(customer);

        entityManager.flush();
        entityManager.clear();

        Optional<Customer> result =
                customerPersistencePort.findById(
                        savedCustomer.getCustomerId()
                );

        assertThat(result).isPresent();

        Customer foundCustomer = result.get();

        assertThat(foundCustomer.getCustomerId()).isNotNull();
        assertThat(foundCustomer.getUserId())
                .isEqualTo("login-test-user");
        assertThat(foundCustomer.getPasswordHash())
                .isEqualTo(PASSWORD_HASH);
        assertThat(foundCustomer.getUserName()).isEqualTo("홍길동");
        assertThat(foundCustomer.getBirthDate())
                .isEqualTo(LocalDate.of(1990, 1, 1));
        assertThat(foundCustomer.getEmail())
                .isEqualTo("login-test@example.com");
        assertThat(foundCustomer.getPhoneNumber())
                .isEqualTo("01012345678");
        assertThat(foundCustomer.getLoginFailureCount()).isEqualTo(3);
        assertThat(foundCustomer.isAccountLocked()).isFalse();
        assertThat(foundCustomer.getLastLoginAt())
                .isEqualTo(lastLoginAt);
        assertThat(foundCustomer.getLastLoginIp())
                .isEqualTo("127.0.0.1");
        assertThat(foundCustomer.getPreviousLoginAt())
                .isEqualTo(previousLoginAt);
        assertThat(foundCustomer.getPasswordChangedAt())
                .isEqualTo(passwordChangedAt);
        assertThat(foundCustomer.getJoinedAt())
                .isEqualTo(joinedAt);
        assertThat(foundCustomer.getCreatedAt()).isNotNull();
        assertThat(foundCustomer.getUpdatedAt()).isNotNull();
    }

    // userId 전용 Repository 조회 메서드와 Mapper 연결 검증
    @Test
    @DisplayName("로그인 아이디로 저장된 고객을 조회한다")
    void findByUserId() {
        Customer savedCustomer =
                customerPersistencePort.save(createCustomer());

        entityManager.flush();
        entityManager.clear();

        Optional<Customer> result =
                customerPersistencePort.findByUserId("adapter-user");

        assertThat(result).isPresent();
        assertThat(result.get().getCustomerId())
                .isEqualTo(savedCustomer.getCustomerId());
        assertThat(result.get().getUserId())
                .isEqualTo("adapter-user");
    }

    // 기존 고객은 범용 save로 다시 저장할 수 없음
    @Test
    @DisplayName("customerId가 있는 기존 고객은 신규 저장에서 거부한다")
    void rejectSavingExistingCustomer() {
        Customer savedCustomer =
                customerPersistencePort.save(createCustomer());

        assertThatThrownBy(() ->
                customerPersistencePort.save(savedCustomer)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("신규 고객은 customerId가 없어야 합니다.");
    }

    // 변경된 실패 횟수와 잠금 상태가 MySQL에 반영되는지 검증
    @Test
    @DisplayName("기존 고객의 로그인 실패 횟수와 잠금 상태를 저장한다")
    void updateLoginFailureState() {
        Customer savedCustomer =
                customerPersistencePort.save(createCustomer());

        entityManager.flush();
        entityManager.clear();

        savedCustomer.recordLoginFailure();
        savedCustomer.recordLoginFailure();

        customerPersistencePort.updateLoginFailureState(savedCustomer);

        entityManager.flush();
        entityManager.clear();

        Customer updatedCustomer =
                customerPersistencePort.findById(
                                savedCustomer.getCustomerId()
                        )
                        .orElseThrow();

        assertThat(updatedCustomer.getLoginFailureCount()).isEqualTo(5);
        assertThat(updatedCustomer.isAccountLocked()).isTrue();
    }

    // 변경된 로그인 성공 상태와 접속 이력이 MySQL에 반영되는지 검증
    @Test
    @DisplayName("로그인 성공 상태와 최근 접속정보를 저장한다")
    void updateLoginSuccessState() {
        Customer savedCustomer =
                customerPersistencePort.save(createCustomer());

        entityManager.flush();
        entityManager.clear();

        LocalDateTime previousLastLoginAt =
                savedCustomer.getLastLoginAt();

        LocalDateTime newLoginAt =
                LocalDateTime.of(2026, 8, 12, 10, 0);

        savedCustomer.recordLoginSuccess(
                newLoginAt,
                "192.168.0.10"
        );

        customerPersistencePort.updateLoginSuccessState(savedCustomer);

        entityManager.flush();
        entityManager.clear();

        Customer updatedCustomer =
                customerPersistencePort.findById(
                                savedCustomer.getCustomerId()
                        )
                        .orElseThrow();

        assertThat(updatedCustomer.getLoginFailureCount()).isZero();
        assertThat(updatedCustomer.isAccountLocked()).isFalse();
        assertThat(updatedCustomer.getPreviousLoginAt())
                .isEqualTo(previousLastLoginAt);
        assertThat(updatedCustomer.getLastLoginAt())
                .isEqualTo(newLoginAt);
        assertThat(updatedCustomer.getLastLoginIp())
                .isEqualTo("192.168.0.10");
    }

    // 변경된 휴대폰 번호와 이메일만 MySQL에 반영되는지 검증
    @Test
    @DisplayName("기존 고객의 휴대폰 번호와 이메일을 저장한다")
    void updateContactInfo() {
        Customer savedCustomer =
                customerPersistencePort.save(createCustomer());
        entityManager.flush();
        entityManager.clear();

        savedCustomer.changeContactInfo(
                "01087654321",
                "new-adapter-user@example.com"
        );

        Customer updated = customerPersistencePort.updateContactInfo(
                savedCustomer
        );
        entityManager.flush();
        entityManager.clear();

        Customer found = customerPersistencePort.findById(
                updated.getCustomerId()
        ).orElseThrow();
        assertThat(found.getPhoneNumber()).isEqualTo("01087654321");
        assertThat(found.getEmail())
                .isEqualTo("new-adapter-user@example.com");
        assertThat(found.getUserId()).isEqualTo("adapter-user");
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    // 통합 테스트에서 사용할 신규 고객 생성
    @Test
    @DisplayName("회원가입 중복확인을 위해 아이디와 이메일 존재 여부를 조회한다")
    void checksRegistrationDuplicates() {
        customerPersistencePort.save(createCustomer());
        entityManager.flush();

        assertThat(customerPersistencePort.existsByUserId("adapter-user"))
                .isTrue();
        assertThat(customerPersistencePort.existsByEmail(
                "adapter-user@example.com"
        )).isTrue();
        assertThat(customerPersistencePort.existsByUserId("unused-user"))
                .isFalse();
        assertThat(customerPersistencePort.existsByEmail(
                "unused@example.com"
        )).isFalse();
    }

    @Test
    @DisplayName("원장 고객 식별자로 이미 가입한 고객이 있는지 조회한다")
    void checksExistingBankCustomerDuplicate() {
        customerPersistencePort.save(Customer.register(
                "bank-user",
                "BANK_CUSTOMER_001",
                PASSWORD_HASH,
                "홍길동",
                LocalDate.of(1990, 1, 1),
                "bank-user@example.com",
                "01012345678",
                LocalDateTime.of(2026, 1, 1, 9, 0)
        ));
        entityManager.flush();

        assertThat(customerPersistencePort.existsByExistingBankCustomerId(
                "BANK_CUSTOMER_001"
        )).isTrue();
        assertThat(customerPersistencePort.existsByExistingBankCustomerId(
                "BANK_CUSTOMER_002"
        )).isFalse();
    }

    private Customer createCustomer() {
        LocalDateTime joinedAt =
                LocalDateTime.of(2026, 1, 1, 9, 0);

        return Customer.restore(
                null,
                "adapter-user",
                null,
                PASSWORD_HASH,
                "홍길동",
                LocalDate.of(1990, 1, 1),
                "adapter-user@example.com",
                "01012345678",
                3,
                false,
                LocalDateTime.of(2026, 8, 10, 9, 0),
                "127.0.0.1",
                LocalDateTime.of(2026, 8, 9, 9, 0),
                LocalDateTime.of(2026, 7, 1, 10, 0),
                joinedAt,
                null,
                null
        );
    }
}
