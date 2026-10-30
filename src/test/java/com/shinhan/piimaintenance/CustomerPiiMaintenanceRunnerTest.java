package com.shinhan.piimaintenance;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.customer.adapter.out.persistence.CustomerPiiBackfillService;
import com.shinhan.corebank.customer.adapter.out.persistence.CustomerPiiVerificationService;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;

class CustomerPiiMaintenanceRunnerTest {

    private CustomerPiiBackfillService backfillService;
    private CustomerPiiVerificationService verifier;
    private DataSource dataSource;
    private MockEnvironment environment;

    @BeforeEach
    void setUp() {
        backfillService = mock(CustomerPiiBackfillService.class);
        verifier = mock(CustomerPiiVerificationService.class);
        dataSource = mock(DataSource.class);

        environment = new MockEnvironment()
                .withProperty("app.pii.maintenance.action", "verify")
                .withProperty("app.pii.enabled", "true")
                .withProperty("app.pii.read-encrypted", "false")
                .withProperty("app.pii.maintenance.expected-db-host", "localhost")
                .withProperty("app.pii.maintenance.expected-db-name", "minicore");
    }

    @Test
    void rejectsUnknownAction() {
        environment.setProperty("app.pii.maintenance.action", "invalid");

        assertThatThrownBy(this::runRunner)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("backfill 또는 verify");

        verifyNoInteractions(dataSource, backfillService, verifier);
    }

    @Test
    void rejectsDisabledEncryption() {
        environment.setProperty("app.pii.enabled", "false");

        assertThatThrownBy(this::runRunner)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.pii.enabled=true");

        verifyNoInteractions(dataSource, backfillService, verifier);
    }

    @Test
    void rejectsEncryptedReadMode() {
        environment.setProperty("app.pii.read-encrypted", "true");

        assertThatThrownBy(this::runRunner)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.pii.read-encrypted=false");

        verifyNoInteractions(dataSource, backfillService, verifier);
    }

    @Test
    void rejectsMissingExpectedHost() {
        environment.setProperty("app.pii.maintenance.expected-db-host", "");

        assertThatThrownBy(this::runRunner)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expected-db-host");

        verifyNoInteractions(dataSource, backfillService, verifier);
    }

    @Test
    void rejectsMissingExpectedDatabase() {
        environment.setProperty("app.pii.maintenance.expected-db-name", " ");

        assertThatThrownBy(this::runRunner)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expected-db-name");

        verifyNoInteractions(dataSource, backfillService, verifier);
    }

    @Test
    void rejectsUnexpectedDatabaseHost() throws Exception {
        mockDatabase("other-host", "minicore");

        assertThatThrownBy(this::runRunner)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("승인된 접속 대상");

        verifyNoInteractions(backfillService, verifier);
    }

    @Test
    void rejectsUnexpectedDatabaseName() throws Exception {
        mockDatabase("localhost", "other_database");

        assertThatThrownBy(this::runRunner)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("승인된 접속 대상");

        verifyNoInteractions(backfillService, verifier);
    }

    @Test
    void executesBackfillAfterPrecheck() throws Exception {
        environment.setProperty("app.pii.maintenance.action", "backfill");
        mockDatabase("localhost", "minicore");

        when(verifier.verifyExistingEncryptedRows()).thenReturn(report(1, 0, 0));
        when(backfillService.backfill(500, 10_000)).thenReturn(new CustomerPiiBackfillService.BackfillReport(1, 1, 0));

        runRunner();

        InOrder order = inOrder(verifier, backfillService);
        order.verify(verifier).verifyExistingEncryptedRows();
        order.verify(backfillService).backfill(500, 10_000);
    }

    @Test
    void stopsBackfillWhenExistingDataIsInconsistent() throws Exception {
        environment.setProperty("app.pii.maintenance.action", "backfill");
        mockDatabase("localhost", "minicore");

        // 기존 이메일 암호문과 평문이 일치하지 않는 상태.
        when(verifier.verifyExistingEncryptedRows()).thenReturn(report(1, 0, 1));

        assertThatThrownBy(this::runRunner)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("기존 개인정보 암호문 검증에 실패");

        verifyNoInteractions(backfillService);
    }

    @Test
    void rejectsIncompleteBackfill() throws Exception {
        environment.setProperty("app.pii.maintenance.action", "backfill");
        mockDatabase("localhost", "minicore");

        when(verifier.verifyExistingEncryptedRows()).thenReturn(report(1, 0, 0));
        when(backfillService.backfill(500, 10_000)).thenReturn(new CustomerPiiBackfillService.BackfillReport(5, 1, 2));

        assertThatThrownBy(this::runRunner)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PII 백필 미완료");
    }

    @Test
    void executesVerificationSuccessfully() throws Exception {
        mockDatabase("localhost", "minicore");

        when(verifier.verifyAll()).thenReturn(report(5, 0, 0));

        runRunner();

        verify(verifier).verifyAll();
        verifyNoInteractions(backfillService);
    }

    @Test
    void rejectsVerificationMismatch() throws Exception {
        mockDatabase("localhost", "minicore");

        when(verifier.verifyAll()).thenReturn(report(5, 1, 0));

        assertThatThrownBy(this::runRunner)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("전체 고객 PII 검증에 실패");

        verifyNoInteractions(backfillService);
    }

    @Test
    void rejectsVerificationWithNoCustomers() throws Exception {
        mockDatabase("localhost", "minicore");

        when(verifier.verifyAll()).thenReturn(report(0, 0, 0));

        assertThatThrownBy(this::runRunner)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("전체 고객 PII 검증에 실패");
    }

    private void mockDatabase(String host, String databaseName) throws Exception {
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getURL()).thenReturn("jdbc:mysql://" + host + ":3306/minicore");
        when(connection.getCatalog()).thenReturn(databaseName);
    }

    private void runRunner() throws Exception {
        CustomerPiiMaintenanceRunner runner =
                new CustomerPiiMaintenanceRunner(backfillService, verifier, dataSource, environment);

        runner.run(new DefaultApplicationArguments());
    }

    private CustomerPiiVerificationService.VerificationReport report(long checked, long missing, long emailMismatch) {
        return new CustomerPiiVerificationService.VerificationReport(checked, missing, emailMismatch, 0, 0, 0);
    }
}
