package com.shinhan.corebank.otp.application.service;

import com.shinhan.corebank.otp.api.OtpTransactionType;
import com.shinhan.corebank.otp.application.port.in.IssueOtpCommand;
import com.shinhan.corebank.otp.application.port.in.IssueOtpResult;
import com.shinhan.corebank.otp.application.port.out.OtpIssueLockPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

// OTP 발급 서비스가 고객 잠금을 트랜잭션 처리 전후로 획득하고 해제하는지 검증한다.
@ExtendWith(MockitoExtension.class)
class OtpIssueServiceTest {

    @Mock OtpIssueLockPort issueLockPort;
    @Mock OtpIssueProcessor processor;

    @Test
    @DisplayName("고객별 잠금 안에서 OTP를 발급하고 커밋 이후 잠금을 해제한다")
    void issuesWithinCustomerLock() {
        OtpIssueService service = new OtpIssueService(
                issueLockPort,
                processor,
                new OtpTransactionDataValidator()
        );
        IssueOtpCommand command = new IssueOtpCommand(
                1L,
                OtpTransactionType.IMMEDIATE_TRANSFER,
                Map.of("amount", 100_000L)
        );
        IssueOtpResult expected = new IssueOtpResult("OTP_REQ_test", "012345", 180);
        when(issueLockPort.tryAcquire(any(), any())).thenReturn(Optional.of("owner-1"));
        when(processor.issue(command)).thenReturn(expected);

        IssueOtpResult result = service.issue(command);

        InOrder ordered = inOrder(issueLockPort, processor);
        ordered.verify(issueLockPort).tryAcquire(any(), any());
        ordered.verify(processor).issue(command);
        ordered.verify(issueLockPort).release(1L, "owner-1");
        assertThat(result).isEqualTo(expected);
    }
}
