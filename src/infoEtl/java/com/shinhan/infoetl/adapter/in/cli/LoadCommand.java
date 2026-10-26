package com.shinhan.infoetl.adapter.in.cli;

import java.time.LocalDate;

// 하루만 적재하면 from == to == baseDate이고 range가 false다.
// --auto는 날짜 없이 경계일부터 가장 최근 cutoff까지 밀린 날짜를 적재한다.
public record LoadCommand(
        LocalDate from, LocalDate to, boolean range, boolean rebuild, boolean auto, boolean verifyPii) {

    static LoadCommand automatic() {
        return new LoadCommand(null, null, false, false, true, false);
    }

    // --verify-pii는 날짜 없이 마트만 검사한다.
    static LoadCommand pii() {
        return new LoadCommand(null, null, false, false, false, true);
    }
}
