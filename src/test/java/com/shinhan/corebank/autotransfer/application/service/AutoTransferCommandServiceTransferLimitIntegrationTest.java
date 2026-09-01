package com.shinhan.corebank.autotransfer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.autotransfer.application.port.in.AutoTransferRegisterCommand;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.limit.domain.exception.LmtErrorCode;
import com.shinhan.corebank.otp.api.OtpAuthTokenVerifier;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

// TransferLimitAdapter는 이제 @Profile 제한 없이 항상 활성화돼(#187 리뷰 반영, ADR-0002),
// 별도로 real adapter를 조립하지 않아도 스프링이 주입하는 AutoTransferCommandService가 이미
// limit.TransferLimitQueryUseCase -> 실제 transfer_limit 테이블 경로를 그대로 탄다. 고객별로 심어둔
// 커스텀 한도값으로 실제 차단/통과가 갈리는지 증명한다(vsopsw 리뷰, PR #278).
@Transactional
class AutoTransferCommandServiceTransferLimitIntegrationTest extends IntegrationTestSupport {

    @Autowired
    AutoTransferCommandService autoTransferCommandService;

    @Autowired
    EntityManager entityManager;

    // 실제 OtpAuthTokenVerifier(Redis 기반)를 태우지 않기 위해 Mock으로 대체한다 —
    // AutoTransferControllerTest와 동일한 패턴(otp_integration_guide.md 연동 전 관례).
    @MockitoBean
    OtpAuthTokenVerifier otpAuthTokenVerifier;

    private static final AtomicLong CUSTOMER_SEQ = new AtomicLong();
    private static final AtomicLong ACCOUNT_SEQ = new AtomicLong();

    private Long withdrawalCustomerId;
    private Long withdrawalAccountId;
    private String depositAccountNumber;

    @BeforeEach
    void setUp() {
        withdrawalCustomerId = insertCustomer();
        withdrawalAccountId = insertAccount(withdrawalCustomerId);
        Long depositCustomerId = insertCustomer();
        depositAccountNumber = accountNumberOf(insertAccount(depositCustomerId));
    }

    @Test
    @DisplayName("고객별로 심어둔 1회한도보다 큰 금액으로 등록하면 실제 DB 값 기준으로 LMT0002를 던진다")
    void register_exceedsCustomerSpecificLimit_throwsLmt0002() {
        insertTransferLimit(withdrawalCustomerId, 5_000L);
        AutoTransferRegisterCommand command = registerCommandBuilder(10_000L).build();

        assertThatThrownBy(() -> autoTransferCommandService.register(command))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(LmtErrorCode.ONE_TIME_LIMIT_EXCEEDED));
    }

    @Test
    @DisplayName("고객별로 심어둔 1회한도 이내 금액이면 실제 DB 값 기준으로 통과한다")
    void register_withinCustomerSpecificLimit_succeeds() {
        insertTransferLimit(withdrawalCustomerId, 50_000L);
        AutoTransferRegisterCommand command = registerCommandBuilder(10_000L).build();

        assertThat(autoTransferCommandService.register(command)).isNotNull();
    }

    private AutoTransferRegisterCommand.AutoTransferRegisterCommandBuilder registerCommandBuilder(Long amount) {
        return AutoTransferRegisterCommand.builder()
                .customerId(withdrawalCustomerId)
                .withdrawalAccountId(withdrawalAccountId)
                .depositAccountNumber(depositAccountNumber)
                .payeeName("홍길동")
                .amount(amount)
                .cycleMonths(1)
                .transferDay(15)
                .startDate(LocalDate.now().plusDays(10))
                .endDate(LocalDate.now().plusMonths(12))
                .accountPasswordAuthToken("token")
                .otpAuthToken("otp-token")
                .requestIp("127.0.0.1");
    }

    private void insertTransferLimit(Long customerId, long oneTimeLimit) {
        entityManager
                .createNativeQuery(
                        "INSERT INTO transfer_limit (customer_id, one_time_limit, daily_limit, created_at, updated_at) "
                                + "VALUES (:customerId, :oneTimeLimit, :dailyLimit, NOW(), NOW())")
                .setParameter("customerId", customerId)
                .setParameter("oneTimeLimit", oneTimeLimit)
                .setParameter("dailyLimit", oneTimeLimit * 2)
                .executeUpdate();
    }

    private String accountNumberOf(Long accountId) {
        return (String) entityManager
                .createNativeQuery("SELECT account_number FROM account WHERE account_id = :id")
                .setParameter("id", accountId)
                .getSingleResult();
    }

    private Long insertCustomer() {
        long seq = CUSTOMER_SEQ.incrementAndGet();
        entityManager
                .createNativeQuery(
                        "INSERT INTO customer (user_id, password_hash, user_name, birth_date, email, phone_number, joined_at, created_at, updated_at) "
                                + "VALUES (:userId, 'x', '홍길동', '1990-01-01', :email, '01012345678', NOW(), NOW(), NOW())")
                .setParameter("userId", "u" + seq)
                .setParameter("email", "test" + seq + "@test.com")
                .executeUpdate();
        return ((Number) entityManager
                        .createNativeQuery("SELECT LAST_INSERT_ID()")
                        .getSingleResult())
                .longValue();
    }

    private Long insertAccount(Long customerId) {
        String accountNumber = String.format("%012d", ACCOUNT_SEQ.incrementAndGet());
        entityManager
                .createNativeQuery(
                        "INSERT INTO account (account_number, customer_id, account_type, status, password_hash, "
                                + "withdrawal_registered, withdrawal_registered_at, opened_date, created_at, updated_at) "
                                + "VALUES (:accountNumber, :customerId, 'DEMAND_DEPOSIT', 'ACTIVE', 'x', TRUE, NOW(), NOW(), NOW(), NOW())")
                .setParameter("accountNumber", accountNumber)
                .setParameter("customerId", customerId)
                .executeUpdate();
        return ((Number) entityManager
                        .createNativeQuery("SELECT LAST_INSERT_ID()")
                        .getSingleResult())
                .longValue();
    }
}
