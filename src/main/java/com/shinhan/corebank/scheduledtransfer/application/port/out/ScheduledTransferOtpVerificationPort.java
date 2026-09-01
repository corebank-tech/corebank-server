package com.shinhan.corebank.scheduledtransfer.application.port.out;

import java.time.LocalDate;
import java.util.List;

public interface ScheduledTransferOtpVerificationPort {

    void verifyRegisterAndConsume(
            String otpAuthToken,
            Long customerId,
            Long withdrawalAccountId,
            String depositAccountNumber,
            Long amount,
            LocalDate scheduledDate);

    // 다건 취소는 토큰 하나가 선택한 id 조합 전체를 덮고 1회만 소비된다.
    // scheduledTransferIds는 오름차순 정렬·중복 제거된 상태여야 한다(ScheduledTransferCancelCommand 참고)
    void verifyCancelAndConsume(String otpAuthToken, Long customerId, List<Long> scheduledTransferIds);
}
