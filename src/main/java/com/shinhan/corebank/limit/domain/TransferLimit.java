package com.shinhan.corebank.limit.domain;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
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

    private TransferLimit(Long customerId, long oneTimeLimit, long dailyLimit) {
        this.customerId = customerId;
        this.oneTimeLimit = oneTimeLimit;
        this.dailyLimit = dailyLimit;
    }

    /** 신규 가입 고객에게 정책 기본값을 부여한다. */
    public static TransferLimit create(Long customerId) {
        return new TransferLimit(customerId, DEFAULT_ONE_TIME_LIMIT, DEFAULT_DAILY_LIMIT);
    }

    /** 영속화된 값을 도메인 객체로 되살린다. 이미 저장된 값이므로 검증하지 않는다. */
    public static TransferLimit restore(Long customerId, long oneTimeLimit, long dailyLimit) {
        return new TransferLimit(customerId, oneTimeLimit, dailyLimit);
    }

    /**
     * 바꾸려는 두 한도가 서로 맞는지만 본다. 객체 없이 부를 수 있어 <b>OTP 를 소모하기 전에</b>
     * 호출할 수 있다 - 1회 &gt; 1일 같은 입력 실수로 토큰이 낭비되면 사용자가 인증을 처음부터
     * 다시 받아야 한다.
     *
     * <p>정책 상한(POL-015·016)은 요청 DTO 의 Bean Validation 이 담당한다(CMN0001).
     * 경계를 0 초과로 잡은 것은 transfer_limit 의 ck_tl_positive 제약과 맞추기 위해서다.
     */
    public static void validateOrder(long oneTimeLimit, long dailyLimit) {
        if (oneTimeLimit <= 0 || dailyLimit <= 0) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "이체한도는 0보다 커야 합니다.");
        }
        if (oneTimeLimit > dailyLimit) {
            throw new BusinessException(LmtErrorCode.ONE_TIME_LIMIT_OVER_DAILY);
        }
    }

    /**
     * 한도를 변경한다. 호출 전에 validateOrder 로 이미 걸러지지만 여기서도 검사한다 - 그건
     * 사용자 입력을 막는 것이고 이건 이 객체의 불변식이라, 웹을 거치지 않는 배치나 테스트가
     * 생겨도 깨지지 않게 한다.
     */
    public void update(long newOneTimeLimit, long newDailyLimit) {
        validateOrder(newOneTimeLimit, newDailyLimit);
        this.oneTimeLimit = newOneTimeLimit;
        this.dailyLimit = newDailyLimit;
    }
}
