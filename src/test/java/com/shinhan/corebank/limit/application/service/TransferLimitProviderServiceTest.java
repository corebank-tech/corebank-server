package com.shinhan.corebank.limit.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.limit.application.port.out.TransferLimitQueryPort;
import com.shinhan.corebank.limit.domain.TransferLimit;
import com.shinhan.corebank.limit.domain.exception.LmtErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransferLimitProviderService 테스트")
class TransferLimitProviderServiceTest {

    private static final Long CUSTOMER_ID = 1L;

    @Mock
    private TransferLimitQueryPort transferLimitQueryPort;

    private TransferLimitProviderService service() {
        return new TransferLimitProviderService(transferLimitQueryPort);
    }

    @Test
    @DisplayName("1회 이체한도만 조회하면 한도 행의 1회 한도를 그대로 돌려준다")
    void findOneTimeLimit_limitExists_returnsStoredOneTimeLimit() {
        // given
        when(transferLimitQueryPort.findByCustomerId(CUSTOMER_ID))
                .thenReturn(Optional.of(TransferLimit.restore(CUSTOMER_ID, 2_000_000L, 8_000_000L)));

        // when
        long oneTimeLimit = service().findOneTimeLimit(CUSTOMER_ID);

        // then - 등록 검증에는 1회 한도만 필요하다. 사용액까지 읽으면 등록 요청마다 쓰지 않는 쿼리가 한 건씩 나간다.
        assertThat(oneTimeLimit).isEqualTo(2_000_000L);
        verify(transferLimitQueryPort, never()).findUsage(any(), any());
    }

    @Test
    @DisplayName("한도 행이 없는 고객은 정책 기본값으로 응답하지 않고 LMT9001 로 거부한다")
    void findOneTimeLimit_limitRowMissing_rejectsWithLmt9001() {
        // given - 가입 연계(REQ-TRSF-029)와 백필이 보장하므로, 행이 없다면 데이터 결함이다
        when(transferLimitQueryPort.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service().findOneTimeLimit(CUSTOMER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(LmtErrorCode.TRANSFER_LIMIT_NOT_FOUND);
    }
}
