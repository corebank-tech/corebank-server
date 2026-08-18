package com.shinhan.corebank.customer.adapter.in.web;

import com.shinhan.corebank.customer.application.port.in.LoginStatusResult;

import java.time.LocalDateTime;

public record LoginStatusResponse(LocalDateTime previousLoginAt, String previousLoginIp) {
    public static LoginStatusResponse from(LoginStatusResult result) {
        return new LoginStatusResponse(result.previousLoginAt(), result.previousLoginIp());
    }
}
