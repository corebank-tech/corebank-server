package com.shinhan.infoetl.application.port.out;

import com.shinhan.infoetl.domain.BalanceRow;
import java.time.LocalDate;
import java.util.List;

public interface ReplicaBalancePort {

    List<BalanceRow> readBalances(LocalDate baseDate);
}
