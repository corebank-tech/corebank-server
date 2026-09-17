package com.shinhan.corebank.auth.application.port.in;

public record IssuePasswordResetCommand(String userId, String customerName, String email) {}
