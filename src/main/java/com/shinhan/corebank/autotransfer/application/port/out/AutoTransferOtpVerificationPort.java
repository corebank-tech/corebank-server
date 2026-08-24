package com.shinhan.corebank.autotransfer.application.port.out;

import java.time.LocalDate;
import java.util.List;

public interface AutoTransferOtpVerificationPort {

    void verifyRegisterAndConsume(String otpAuthToken, Long customerId, Long withdrawalAccountId,
                                   String depositAccountNumber, Long amount, Integer cycleMonths,
                                   Integer transferDay, LocalDate startDate, LocalDate endDate);

    void verifyChangeAndConsume(String otpAuthToken, Long customerId, Long autoTransferId,
                                 Long amount, Integer cycleMonths, LocalDate endDate);

    // 다건 해지는 토큰 하나가 선택한 id 조합 전체를 덮고 1회만 소비된다.
    // autoTransferIds는 오름차순 정렬·중복 제거된 상태여야 한다(AutoTransferCancelCommand 참고)
    void verifyCancelAndConsume(String otpAuthToken, Long customerId, List<Long> autoTransferIds);
}
