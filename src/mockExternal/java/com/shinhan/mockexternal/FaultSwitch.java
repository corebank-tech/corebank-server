package com.shinhan.mockexternal;

import java.time.Duration;

/**
 * 장애 스위치. 관리 HTTP로 서버가 떠 있는 동안 바꾼다.
 *
 * 세 장애 모두 요청은 정상 처리해 장부에 남긴다. 상대 기관은 처리했는데 응답만 잃거나 늦거나 겹치는 경우가
 * 재현하려는 상황이기 때문이다(PH-36).
 */
final class FaultSwitch {

    enum Mode {
        /** 정상 응답 */
        NONE,
        /** delay만큼 기다렸다가 응답 */
        DELAY,
        /** 처리는 하고 응답을 보내지 않는다. 연결은 상대가 끊을 때까지 잡고 있는다 */
        NO_RESPONSE,
        /** 같은 응답을 두 번 보낸다 */
        DUPLICATE
    }

    record Fault(Mode mode, Duration delay) {}

    private volatile Fault current = new Fault(Mode.NONE, Duration.ZERO);

    Fault current() {
        return current;
    }

    void set(Mode mode, Duration delay) {
        current = new Fault(mode, mode == Mode.DELAY ? delay : Duration.ZERO);
    }
}
