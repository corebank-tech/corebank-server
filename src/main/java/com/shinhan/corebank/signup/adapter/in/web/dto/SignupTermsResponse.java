package com.shinhan.corebank.signup.adapter.in.web.dto;

import java.util.List;

public record SignupTermsResponse(
        List<SignupTermResponse> items
) {
}
