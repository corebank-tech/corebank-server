package com.shinhan.corebank.limit.domain;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.limit.domain.exception.LmtErrorCode;

import lombok.Getter;

/**
 * 고객별 이체한도. 고객당 1행이며 1회 한도는 1일 한도를 넘을 수 없다(LMT0004).
 * 정책 기본값은 REQ-TRSF-029 에 따라 1회 100만 / 1일 500만이다(POL-013·014).
 */
@Getter
public class TransferLimit {

    public static final long DEFAULT_ONE_TIME_LIMIT = 1_000_000L;
    public static final long DEFAULT_DAILY_LIMIT = 5_000_000L;

    private final Long customerId;
    private long oneTimeLimit;
    private long dailyLimit;
    private final Long version;

    private TransferLimit(Long customerId, long oneTimeLimit, long dailyLimit, Long version) {
        this.customerId = customerId;
        this.oneTimeLimit = oneTimeLimit;
        this.dailyLimit = dailyLimit;
        this.version = version;
    }

    /** 신규 가입 고객에게 정책 기본값을 부여한다. */
    public static TransferLimit create(Long customerId) {
        return new TransferLimit(customerId, DEFAULT_ONE_TIME_LIMIT, DEFAULT_DAILY_LIMIT, null);
    }

    /** 영속화된 값을 도메인 객체로 되살린다. 이미 저장된 값이므로 검증하지 않는다. */
    public static TransferLimit restore(Long customerId, long oneTimeLimit, long dailyLimit, Long version) {
        return new TransferLimit(customerId, oneTimeLimit, dailyLimit, version);
    }

    /**
     * 한도를 변경한다. 정책 상한 검사는 요청 DTO 의 Bean Validation 이 담당하고(CMN0001),
     * 여기서는 두 한도 사이의 관계와 최소값만 본다. 경계를 0 초과로 잡은 것은
     * transfer_limit 의 ck_tl_positive 제약과 맞추기 위해서다.
     */
    public void update(long newOneTimeLimit, long newDailyLimit) {
        if (newOneTimeLimit <= 0 || newDailyLimit <= 0) {
            throw new IllegalArgumentException("이체한도는 0보다 커야 합니다.");
        }
        if (newOneTimeLimit > newDailyLimit) {
            throw new BusinessException(LmtErrorCode.ONE_TIME_LIMIT_OVER_DAILY);
        }
        this.oneTimeLimit = newOneTimeLimit;
        this.dailyLimit = newDailyLimit;
    }
}
