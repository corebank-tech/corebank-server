package com.shinhan.infoetl.domain;

import java.util.List;

public record PiiVerificationResult(List<String> violations, long scannedColumns) {

    public boolean passed() {
        return violations.isEmpty();
    }
}
