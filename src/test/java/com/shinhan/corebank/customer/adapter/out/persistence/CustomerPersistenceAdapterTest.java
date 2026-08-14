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
                PASSWORD_HASH,
                "홍길동",
                LocalDate.of(1990, 1, 1),
                "login-test@example.com",
                "01012345678",
                3,
                false,
                "OPENED_DATE_ASC",
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
        assertThat(foundCustomer.getDisplayOrderType())
                .isEqualTo("OPENED_DATE_ASC");
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

        customerPersistencePort.save(savedCustomer);

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

        customerPersistencePort.save(savedCustomer);

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

    // 통합 테스트에서 사용할 신규 고객 생성
    private Customer createCustomer() {
        LocalDateTime joinedAt =
                LocalDateTime.of(2026, 1, 1, 9, 0);

        return Customer.restore(
                null,
                "adapter-user",
                PASSWORD_HASH,
                "홍길동",
                LocalDate.of(1990, 1, 1),
                "adapter-user@example.com",
                "01012345678",
                3,
                false,
                "OPENED_DATE_ASC",
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
