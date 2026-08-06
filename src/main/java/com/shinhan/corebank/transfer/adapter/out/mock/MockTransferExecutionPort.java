package com.shinhan.corebank.transfer.adapter.out.mock;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.transfer.application.port.in.TransferCommand;
import com.shinhan.corebank.transfer.application.port.in.TransferExecutionUseCase;
import com.shinhan.corebank.transfer.application.port.in.TransferResult;

@Component
@Profile({"local", "test", "scratch"})
public class MockTransferExecutionPort implements TransferExecutionUseCase{

    // 임시 일련번호 생성을 위한 시퀀스 (동시성 보장)
    private final AtomicLong sequence = new AtomicLong(1L);

    @Override
    public TransferResult execute(TransferCommand command) {

        LocalDateTime now = LocalDateTime.now();

        String dateStr = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String channel = command.channel() != null ? command.channel().name() : "WB";
        String seqStr = String.format("%010d",sequence.getAndIncrement());

        // 거래번호 규칙: YYYYMMDD + 채널(2) + 일련(10) = 20자리
        String transactionNumber = dateStr + channel + seqStr;

        return TransferResult.builder()
                .status(ProcessResultStatus.SUCCESS)
                .transactionNumber(transactionNumber)
                .transferredAt(now)
                .withdrawalBalanceAfter(10000L)
                .build();
    }
}
