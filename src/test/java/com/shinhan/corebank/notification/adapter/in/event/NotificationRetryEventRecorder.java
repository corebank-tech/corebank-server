package com.shinhan.corebank.notification.adapter.in.event;

import com.shinhan.corebank.notification.api.NotificationRequestCarrier;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.ApplicationListener;
import org.springframework.resilience.retry.MethodRetryEvent;

/**
 * 알림 리스너(@Retryable 은 이 리스너에만 있다)에서 Spring 이 내는 MethodRetryEvent 를 모은다. 실패한 시도마다 한 번, 재시도를 다 쓰면 마지막에 포기(aborted)
 * 한 번 나온다. 포기 이벤트는 발행 기록을 FAILED 로 남긴 뒤에 나오므로, 그 뒤에는 그 실행이 기록을 더 건드리지 않는다.
 */
public class NotificationRetryEventRecorder implements ApplicationListener<MethodRetryEvent> {

    private final AtomicInteger failedAttempts = new AtomicInteger();
    private final Set<Long> exhaustedRefIds = ConcurrentHashMap.newKeySet();

    @Override
    public void onApplicationEvent(MethodRetryEvent event) {
        if (event.isRetryAborted()) {
            NotificationRequestCarrier carrier =
                    (NotificationRequestCarrier) event.getSource().getArguments()[0];
            exhaustedRefIds.add(carrier.notification().refId());
        } else {
            failedAttempts.incrementAndGet();
        }
    }

    public int failedAttempts() {
        return failedAttempts.get();
    }

    public boolean hasExhaustedRetries(long refId) {
        return exhaustedRefIds.contains(refId);
    }

    public void clear() {
        failedAttempts.set(0);
        exhaustedRefIds.clear();
    }
}
