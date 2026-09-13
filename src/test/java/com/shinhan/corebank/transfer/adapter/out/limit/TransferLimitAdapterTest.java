package com.shinhan.corebank.transfer.adapter.out.limit;

import static org.mockito.Mockito.verify;

import com.shinhan.corebank.limit.api.TransferLimitReserver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("transfer 한도 어댑터 단위 테스트")
class TransferLimitAdapterTest {

    @Mock
    private TransferLimitReserver transferLimitReserver;

    private TransferLimitAdapter adapter;

    @Test
    @DisplayName("checkAndReserve 호출을 limit.api.TransferLimitReserver로 위임한다")
    void checkAndReserve_delegatesToTransferLimitReserver() {
        adapter = new TransferLimitAdapter(transferLimitReserver);

        adapter.checkAndReserve(1L, 500_000L);

        verify(transferLimitReserver).checkAndReserve(1L, 500_000L);
    }
}
