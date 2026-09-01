package com.shinhan.corebank.transfer.application.port.out;

import com.shinhan.corebank.transfer.domain.TransferChannel;
import java.time.LocalDate;

public interface TransferSequencePort {

    /**
     * 영업일자(seqDate)·채널별로 원자적으로 증가하는 20자리 거래번호를 채번한다.
     * 형식: YYYYMMDD(8) + 채널(2) + 일련번호(10, zero-padded)
     */
    String nextTransactionNumber(LocalDate seqDate, TransferChannel channel);
}
