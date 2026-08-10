package com.shinhan.corebank.common.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.IntegrationTestSupport;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * record()는 success 값에 따라 트랜잭션 처리가 다르다.
 * - success=false: REQUIRES_NEW로 즉시 커밋 — 호출자 트랜잭션이 롤백돼도 실패 로그는 남아야 한다.
 * - success=true: AFTER_COMMIT 이벤트로 지연 — 호출자 트랜잭션이 실제로 커밋된 뒤에만 남아야 한다.
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
    @DisplayName("호출자 트랜잭션이 롤백되어도 REQUIRES_NEW 덕분에 감사 로그는 커밋되어 남는다")
    void record_survivesOuterTransactionRollback() {
        TransactionTemplate outerTx = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> outerTx.executeWithoutResult(status -> {
            auditLogService.record(1L, null, AuditEventType.LOGIN, "127.0.0.1", false,
                    Map.of("reason", "비밀번호 불일치"));
            throw new RuntimeException("바깥 트랜잭션 강제 실패");
        })).isInstanceOf(RuntimeException.class);

        assertThat(auditLogJpaRepository.findAll())
                .extracting(AuditLogJpaEntity::getResult)
                .containsExactly("FAILURE");
    }

    @Test
    @DisplayName("정상 흐름(트랜잭션 밖 직접 호출)에서는 성공 감사 로그가 저장된다")
    void record_success_isSaved() {
        auditLogService.record(1L, null, AuditEventType.LOGIN, "127.0.0.1", true,
                Map.of("device", "web"));

        assertThat(auditLogJpaRepository.findAll())
                .extracting(AuditLogJpaEntity::getResult)
                .containsExactly("SUCCESS");
    }

    @Test
    @DisplayName("성공 감사 로그는 호출자 트랜잭션이 커밋되기 전까지는 저장되지 않고, 롤백되면 아예 저장 안 된다")
    void record_success_doesNotSurviveOuterTransactionRollback() {
        TransactionTemplate outerTx = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> outerTx.executeWithoutResult(status -> {
            auditLogService.record(1L, null, AuditEventType.LOGIN, "127.0.0.1", true,
                    Map.of("device", "web"));
            throw new RuntimeException("바깥 트랜잭션 강제 실패");
        })).isInstanceOf(RuntimeException.class);

        // record()가 예전처럼 즉시(REQUIRES_NEW)로 저장했다면 이 롤백과 무관하게 남았을 것이다 —
        // AFTER_COMMIT로 바뀌었으니 바깥 트랜잭션이 커밋을 못 했으므로 아예 저장되지 않아야 한다
        assertThat(auditLogJpaRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("성공 감사 로그는 호출자 트랜잭션이 정상 커밋되면 저장된다")
    void record_success_survivesOuterTransactionCommit() {
        TransactionTemplate outerTx = new TransactionTemplate(transactionManager);

        outerTx.executeWithoutResult(status ->
                auditLogService.record(1L, null, AuditEventType.LOGIN, "127.0.0.1", true,
                        Map.of("device", "web")));

        assertThat(auditLogJpaRepository.findAll())
                .extracting(AuditLogJpaEntity::getResult)
                .containsExactly("SUCCESS");
    }
}
