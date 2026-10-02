package com.shinhan.corebank.common.event;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import java.time.LocalDateTime;

/**
 * 업무 결과가 확정됐을 때 발행하는 도메인 이벤트의 공통 계약. 알림(#393~#396)과 아웃박스(#437)가
 * 이 다섯 값만 보고 처리한다.
 *
 * <p>refId 는 notification.ref_id 규약을 따른다 — 무엇을 가리키는지는 각 이벤트가 밝힌다.
 */
public interface DomainEvent {

    Long customerId();

    Long refId();

    ProcessResultStatus status();

    /** 실패 사유 코드. 성공이면 null. */
    String errorCode();

    LocalDateTime occurredAt();
}
