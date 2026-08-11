package com.shinhan.corebank.common.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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
 * - success=true: 이벤트로 발행해 AuditLogEventListener가 AFTER_COMMIT 시점에 저장 —
 *   호출자 트랜잭션이 실제로 커밋된 뒤에만 남아야 한다. 그 저장이 실패해도(검증 오류 등)
 *   이미 커밋된 호출자 쪽에는 절대 영향을 주면 안 된다.
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
    @DisplayName("성공 감사 로그는 호출자 트랜잭션이 롤백되면 저장되지 않는다")
    void record_success_doesNotSurviveOuterTransactionRollback() {
        TransactionTemplate outerTx = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> outerTx.executeWithoutResult(status -> {
            auditLogService.record(1L, null, AuditEventType.LOGIN, "127.0.0.1", true,
                    Map.of("device", "web"));
            throw new RuntimeException("바깥 트랜잭션 강제 실패");
        })).isInstanceOf(RuntimeException.class);

        // record()가 REQUIRES_NEW로 즉시 저장했다면 이 롤백과 무관하게 남았을 것이다 —
        // AFTER_COMMIT 이벤트라 바깥 트랜잭션이 커밋을 못 했으므로 아예 저장되지 않아야 한다
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

    @Test
    @DisplayName("성공 로그 저장이 실패해도(검증 오류) 이미 커밋된 호출자에게 예외가 전파되지 않는다")
    void record_successWriteFails_doesNotPropagateToCaller() {
        TransactionTemplate outerTx = new TransactionTemplate(transactionManager);

        // LOGIN은 원장 비변경 이벤트라 transactionNumber가 있으면 AuditLogJpaEntity.of()에서
        // 검증 실패(IllegalArgumentException)한다 — AuditLogEventListener가 이 예외를 잡아서
        // 삼켜야 하고, 이미 커밋된 호출자 코드는 이 실패를 전혀 모른 채 정상 종료돼야 한다.
        assertThatCode(() -> outerTx.executeWithoutResult(status ->
                auditLogService.record(1L, "20260101WB0000000001", AuditEventType.LOGIN, "127.0.0.1", true,
                        Map.of("device", "web"))))
                .doesNotThrowAnyException();

        // 검증에 실패했으니 실제로 저장은 안 됐어야 한다 — 실패가 "조용히 무시"됐을 뿐,
        // 잘못된 데이터가 저장된 건 아니라는 것도 함께 확인
        assertThat(auditLogJpaRepository.findAll()).isEmpty();
    }
}
