package com.shinhan.corebank.transfer.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.transfer.application.port.in.DelayedTransferSettingCommand;
import com.shinhan.corebank.transfer.application.port.in.DelayedTransferSettingResult;
import com.shinhan.corebank.transfer.application.port.in.DelayedTransferSettingUseCase;
import com.shinhan.corebank.transfer.application.port.out.DelayedTransferSettingPort;
import com.shinhan.corebank.transfer.application.port.out.TransferAuthTokenVerificationPort;
import com.shinhan.corebank.transfer.domain.DelayedTransferSetting;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DelayedTransferSettingService implements DelayedTransferSettingUseCase {

    private final DelayedTransferSettingPort delayedTransferSettingPort;
    private final TransferAuthTokenVerificationPort transferAuthTokenVerificationPort;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public DelayedTransferSettingResult get(long customerId) {
        return DelayedTransferSettingResult.from(delayedTransferSettingPort
                .findByCustomerId(customerId)
                .orElseGet(() -> DelayedTransferSetting.defaultsFor(customerId)));
    }

    // 범위 검증을 토큰 소비보다 먼저 한다. 행을 잠그고 읽어, 동시에 온 다른 변경이 완화 판정을 우회하지 못하게 한다.
    @Override
    @Transactional
    public DelayedTransferSettingResult change(DelayedTransferSettingCommand command) {
        long customerId = command.customerId();
        DelayedTransferSetting next = DelayedTransferSetting.of(
                customerId, command.enabled(), command.thresholdAmount(), command.delayHours());
        DelayedTransferSetting previous = delayedTransferSettingPort
                .findForUpdateByCustomerId(customerId)
                .orElseGet(() -> DelayedTransferSetting.defaultsFor(customerId));

        if (next.loosens(previous)) {
            if (command.accountPasswordAuthToken() == null
                    || command.accountPasswordAuthToken().isBlank()) {
                throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
            }
            transferAuthTokenVerificationPort.verifyAndConsume(command.accountPasswordAuthToken(), customerId);
        }
        return DelayedTransferSettingResult.from(delayedTransferSettingPort.save(next, LocalDateTime.now(clock)));
    }
}
