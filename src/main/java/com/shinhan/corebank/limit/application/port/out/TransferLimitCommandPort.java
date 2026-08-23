package com.shinhan.corebank.limit.application.port.out;

import java.time.LocalDate;
import java.util.Optional;

import com.shinhan.corebank.limit.domain.TransferLimit;
import com.shinhan.corebank.limit.domain.TransferLimitDailyUsage;

/** 한도 상태를 바꾸는 오퍼레이션. */
public interface TransferLimitCommandPort {

    /** 한도를 새 값으로 <b>덮어쓴다</b>. 고객이 직접 바꾸는 경로 전용이다. */
    TransferLimit save(TransferLimit limit);

    /**
     * 한도 행이 없을 때만 만든다. 이미 있으면 그대로 둔다 - 고객이 올려 둔 한도를 기본값으로
     * 되돌리지 않기 위해서다.
     *
     * @return 이번 호출이 행을 만들었으면 true. 변경 경로가 "행이 없던 고객"임을 로그로 남기는 데 쓴다.
     */
    boolean saveIfAbsent(TransferLimit limit);

    /** 변경을 위해 X-Lock 을 잡고 읽는다. 조회 전용 경로는 TransferLimitQueryPort 를 쓴다. */
    Optional<TransferLimit> findForUpdateByCustomerId(Long customerId);

    /**
     * 이체 실행 중 한도를 읽는 동안 S-Lock 을 잡는다. 이체는 한도 행을 쓰지 않으므로 X-Lock 이
     * 필요 없고, 그 사이 한도 변경(X-Lock)이 끼어들지 못하게만 막으면 된다(write skew 차단).
     * 쓰지 않으니 S→X 승격이 없어 데드락도 생기지 않는다.
     */
    Optional<TransferLimit> findForShareByCustomerId(Long customerId);

    /**
     * 당일 사용액 행에 X-Lock 을 잡고 읽는다. 그날 첫 이체라 행이 없으면 사용액 0 으로 만들어서라도
     * 잠근다 - 없는 행은 잠글 수 없어(SELECT ... FOR UPDATE 가 0 건을 돌려준다) 동시 요청이
     * 서로를 못 보고 지나가기 때문이다. 어떻게 만드는지는 어댑터의 몫이다.
     */
    TransferLimitDailyUsage lockDailyUsage(Long customerId, LocalDate usageDate);

    /** 적립된 사용액을 저장한다. lockDailyUsage 로 잠근 행에만 호출한다. */
    void saveUsage(TransferLimitDailyUsage usage);
}
