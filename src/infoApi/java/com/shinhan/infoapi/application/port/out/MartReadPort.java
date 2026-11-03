package com.shinhan.infoapi.application.port.out;

import com.shinhan.infoapi.domain.DepositBalancePoint;
import com.shinhan.infoapi.domain.ProductSubscriptionLine;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MartReadPort {

    // completed_at이 가장 늦은 COMPLETED 배치의 as_of_date. 적재 이력이 없거나 워터마크가 NULL이면 비어 있다.
    Optional<LocalDate> latestAsOf();

    // from~to 범위의 COMPLETED 영업일별 mart_balance_total. 날짜 오름차순이다.
    List<DepositBalancePoint> depositBalances(LocalDate from, LocalDate to);

    // from~to 안의 가입·해지를 상품별로 센다. 상품명은 상품별로 가장 최근에 적재된 스냅샷이다.
    List<ProductSubscriptionLine> productSubscriptions(LocalDate from, LocalDate to);
}
