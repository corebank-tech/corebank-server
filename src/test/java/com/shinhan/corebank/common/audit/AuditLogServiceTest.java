package com.shinhan.corebank.common.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.IntegrationTestSupport;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * success=true(성공 로그)는 호출자의 앰비언트 트랜잭션에 그대로 합류한다
 * (Fineract 커맨드소싱 방식 - "기록 없이는 처리도 없다"). 저장이 실패하면 예외가 그대로
 * 호출자에게 전파되어, 호출자가 트랜잭션 안에 있었다면 그 트랜잭션(업무 로직 포함) 전체가
 * 롤백된다 - 예전(AFTER_COMMIT + try/catch)처럼 감사 로그 실패를 조용히 삼키지 않는다.
 *
 * success=false(실패 로그)는 반대로 "시도했다"는 사실 자체가 감사 대상이라, 호출자
 * 트랜잭션이 어떤 이유로 롤백되든 REQUIRES_NEW로 즉시 별도 커밋되어 살아남는다.
 */
class AuditLogServiceTest extends IntegrationTestSupport {

    @Autowired
    AuditLogService auditLogService;

    @Autowired
    AuditLogJpaRepository auditLogJpaRepository;

    @Autowired
    PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanUp() {
        auditLogJpaRepository.deleteAll();
    }

    @Test
    @DisplayName("앰비언트 트랜잭션 없이 직접 호출해도 즉시 저장된다")
    void record_withoutAmbientTransaction_savesImmediately() {
        auditLogService.record(1L, null, AuditEventType.LOGIN, "127.0.0.1", true,
                Map.of("device", "web"));

        assertThat(auditLogJpaRepository.findAll())
                .extracting(AuditLogJpaEntity::getResult)
                .containsExactly("SUCCESS");
    }

    @Test
    @DisplayName("트랜잭션 안에서 호출하고 정상 커밋되면 감사 로그도 같이 커밋된다")
    void record_withinTransaction_commitsWithCaller() {
        TransactionTemplate outerTx = new TransactionTemplate(transactionManager);

        outerTx.executeWithoutResult(status ->
                auditLogService.record(1L, null, AuditEventType.LOGIN, "127.0.0.1", true,
                        Map.of("device", "web")));

        assertThat(auditLogJpaRepository.findAll())
                .extracting(AuditLogJpaEntity::getResult)
                .containsExactly("SUCCESS");
    }

    @Test
    @DisplayName("트랜잭션 안에서 호출했는데 그 트랜잭션이 다른 이유로 롤백되면 감사 로그도 같이 사라진다")
    void record_withinTransaction_rollsBackWithCaller() {
        TransactionTemplate outerTx = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> outerTx.executeWithoutResult(status -> {
            auditLogService.record(1L, null, AuditEventType.LOGIN, "127.0.0.1", true,
                    Map.of("device", "web"));
            throw new RuntimeException("호출자 쪽 업무 로직 실패");
        })).isInstanceOf(RuntimeException.class);

        assertThat(auditLogJpaRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("감사 로그 저장 자체가 실패하면(검증 오류) 예외가 호출자에게 그대로 전파된다 - 더 이상 조용히 삼키지 않음")
    void record_saveFails_propagatesExceptionToCaller() {
        // LOGIN은 원장 비변경 이벤트라 transactionNumber가 있으면 AuditLogJpaEntity.of()에서 검증 실패한다
        assertThatThrownBy(() -> auditLogService.record(1L, "20260101WB0000000001", AuditEventType.LOGIN,
                "127.0.0.1", true, Map.of("device", "web")))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(auditLogJpaRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("감사 로그 저장 실패가 같은 트랜잭션 안에서 이미 시도한 다른 저장까지 함께 롤백시킨다")
    void record_saveFails_rollsBackRestOfSameTransaction() {
        TransactionTemplate outerTx = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> outerTx.executeWithoutResult(status -> {
            // 1) 정상적인 저장 시도(원장 비변경 이벤트, transactionNumber 없음 - 검증 통과)
            auditLogService.record(1L, null, AuditEventType.AUTO_TRANSFER_INFO_CHANGE, "127.0.0.1", true,
                    Map.of("action", "register"));
            // 2) 같은 트랜잭션 안에서 검증 실패를 유발하는 두 번째 호출
            auditLogService.record(1L, "20260101WB0000000001", AuditEventType.LOGIN, "127.0.0.1", true,
                    Map.of("device", "web"));
        })).isInstanceOf(IllegalArgumentException.class);

        // 트랜잭션 전체가 롤백됐으므로, 먼저 시도했던 정상 저장 건도 남아있으면 안 된다 -
        // "감사 기록 실패가 이 트랜잭션에서 이미 한 다른 일까지 되돌린다"는 게 이번 재설계의 핵심
        assertThat(auditLogJpaRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("실패 로그(success=false)는 앰비언트 트랜잭션 없이 직접 호출해도 즉시 저장된다")
    void record_failureLog_withoutAmbientTransaction_savesImmediately() {
        auditLogService.record(1L, null, AuditEventType.LOGIN, "127.0.0.1", false,
                Map.of("reason", "wrong-password"));

        assertThat(auditLogJpaRepository.findAll())
                .extracting(AuditLogJpaEntity::getResult)
                .containsExactly("FAILURE");
    }

    @Test
    @DisplayName("실패 로그(success=false)는 호출자 트랜잭션이 다른 이유로 롤백돼도 REQUIRES_NEW로 이미 커밋됐기 때문에 남는다")
    void record_failureLog_survivesCallerTransactionRollback() {
        TransactionTemplate outerTx = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> outerTx.executeWithoutResult(status -> {
            auditLogService.record(1L, null, AuditEventType.LOGIN, "127.0.0.1", false,
                    Map.of("reason", "wrong-password"));
            throw new RuntimeException("호출자 쪽 업무 로직 실패");
        })).isInstanceOf(RuntimeException.class);

        // success=true였다면 이 실패 로그도 같이 사라졌겠지만, success=false는 REQUIRES_NEW로
        // 별도 트랜잭션에 이미 커밋되어 있어서 바깥 트랜잭션의 롤백과 무관하게 남는다
        assertThat(auditLogJpaRepository.findAll())
                .extracting(AuditLogJpaEntity::getResult)
                .containsExactly("FAILURE");
    }

    @Test
    @DisplayName("실패 로그(success=false)도 저장 자체가 실패하면(검증 오류) 예외가 호출자에게 그대로 전파된다")
    void record_failureLog_saveFails_propagatesExceptionToCaller() {
        assertThatThrownBy(() -> auditLogService.record(1L, "20260101WB0000000001", AuditEventType.LOGIN,
                "127.0.0.1", false, Map.of("reason", "wrong-password")))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(auditLogJpaRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("실제 DB 제약 위반(request_ip NOT NULL)이 발생하면 DataIntegrityViolationException이 호출자에게 전파되고, 같은 트랜잭션에서 먼저 저장한 로그도 함께 롤백된다")
    void record_saveFails_realDbConstraintViolation_propagatesAndRollsBackCallerTransaction() {
        // requestIp=null은 AuditLogJpaEntity.of()의 도메인 검증을 통과한다 (검증 대상이 아님) -
        // 그래서 여기서는 IllegalArgumentException이 아니라 실제 DB INSERT 시점의
        // NOT NULL 제약 위반(DataIntegrityViolationException)이 발생한다
        TransactionTemplate outerTx = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> outerTx.executeWithoutResult(status -> {
            auditLogService.record(1L, null, AuditEventType.AUTO_TRANSFER_INFO_CHANGE, "127.0.0.1", true,
                    Map.of("action", "register"));
            auditLogService.record(1L, null, AuditEventType.LOGIN, null, true, Map.of("device", "web"));
        })).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(auditLogJpaRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("실패 로그(success=false)도 실제 DB 제약 위반 시 DataIntegrityViolationException이 호출자에게 전파된다")
    void record_failureLog_saveFails_realDbConstraintViolation_propagatesToCaller() {
        assertThatThrownBy(() -> auditLogService.record(1L, null, AuditEventType.LOGIN, null, false,
                Map.of("reason", "wrong-password")))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(auditLogJpaRepository.findAll()).isEmpty();
    }
}
