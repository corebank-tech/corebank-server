package com.shinhan.corebank.fds.application.port.out;

import com.shinhan.corebank.fds.api.FdsInvestigationInput;

/** Python 조사 실행 확인만 받는다. 보고서 저장과 완료 전이는 Python의 BE 콜백이 소유한다. */
public interface FdsInvestigationAgentPort {
    Result investigate(FdsInvestigationInput input);

    record Result(String investigationId, long transferHoldId, String status) {}

    final class Failure extends RuntimeException {
        private final boolean retryable;

        public Failure(boolean retryable) {
            super("Python 조사 실행에 실패했습니다.");
            this.retryable = retryable;
        }

        public boolean retryable() {
            return retryable;
        }
    }
}
