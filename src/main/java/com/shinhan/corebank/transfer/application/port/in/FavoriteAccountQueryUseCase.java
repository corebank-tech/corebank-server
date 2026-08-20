package com.shinhan.corebank.transfer.application.port.in;

import java.util.List;

public interface FavoriteAccountQueryUseCase {
    List<FavoriteAccountResult> queryAll(Long customerId);
}
