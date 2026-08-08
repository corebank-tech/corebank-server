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
 * record()가 REQUIRES_NEW로 동작해, 호출자 트랜잭션이 롤백돼도
 * 감사 로그는 별도로 커밋되어 남는지 검증한다.
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
    @DisplayName("정상 흐름에서는 성공 감사 로그가 저장된다")
    void record_success_isSaved() {
        auditLogService.record(1L, null, AuditEventType.LOGIN, "127.0.0.1", true,
                Map.of("device", "web"));

        assertThat(auditLogJpaRepository.findAll())
                .extracting(AuditLogJpaEntity::getResult)
                .containsExactly("SUCCESS");
    }
}
