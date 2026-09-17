package com.shinhan.corebank.auth.application.port.in;

public interface IssuePasswordResetUseCase {
    IssuePasswordResetResult issue(IssuePasswordResetCommand command);
}
