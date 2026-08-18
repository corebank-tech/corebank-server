package com.shinhan.corebank.product.application.port.out;

import java.time.LocalDateTime;

public record TermsView(LocalDateTime viewedAt, LocalDateTime viewExpiresAt) {
}
