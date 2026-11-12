package com.shinhan.corebank.fds.application.port.out;

import com.shinhan.corebank.fds.api.FdsInvestigationInput;

/** Python 조사 실행 계약이 확정되면 HTTP 어댑터가 구현할 포트다. */
public interface FdsInvestigationAgentPort {
    Result investigate(FdsInvestigationInput input);

    record Result(String schemaVersion, String reportJson) {}
}
