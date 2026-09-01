package com.shinhan.corebank.account.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record AccountDisplayOrderRequest(
        @Schema(description = "화면에 표시할 순서대로 나열한 계좌 ID 목록", example = "[103, 101, 102]") List<Long> accountIds) {}
