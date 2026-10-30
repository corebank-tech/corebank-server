package com.shinhan.corebank.common.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// #437 이 outbox INSERT 구현으로 교체할 때까지 두는 자리. 리스너가 sink 빈 없이는 뜨지 않는다.
@Slf4j
@Component
public class DomainEventLoggingSink implements DomainEventSink {

    @Override
    public void record(DomainEvent event) {
        log.info(
                "domain event: {} customerId={} refId={} status={}",
                event.getClass().getSimpleName(),
                event.customerId(),
                event.refId(),
                event.status());
    }
}
