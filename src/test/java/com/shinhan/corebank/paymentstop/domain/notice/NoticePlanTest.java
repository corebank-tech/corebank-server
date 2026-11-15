package com.shinhan.corebank.paymentstop.domain.notice;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.paymentstop.domain.DecisionResult;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("판정 결과별 통지 대상(D30)")
class NoticePlanTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 6, 10, 0);

    @Test
    @DisplayName("불수용은 명의인 문자 하나다")
    void rejected() {
        assertThat(targets(NoticePlan.decision(1L, DecisionResult.REJECTED, false, AT)))
                .containsExactly("HOLDER:DECISION_REJECTED:SMS");
    }

    @Test
    @DisplayName("일부 지급정지는 피해자와 금감원 문서이고 지급정지 종료가 아니라 종료 통지는 없다")
    void partial() {
        assertThat(targets(NoticePlan.decision(1L, DecisionResult.PARTIAL, false, AT)))
                .containsExactly("VICTIM:DECISION_PARTIAL:DOCUMENT", "FSS:DECISION_PARTIAL:DOCUMENT");
    }

    @Test
    @DisplayName("수용으로 지급정지가 끝나면 명의인, 피해자, 관련 금융회사에 종료 통지를 더한다")
    void acceptedAndTerminated() {
        assertThat(targets(NoticePlan.decision(1L, DecisionResult.ACCEPTED, true, AT)))
                .containsExactly(
                        "VICTIM:DECISION_ACCEPTED:DOCUMENT",
                        "FSS:DECISION_ACCEPTED:DOCUMENT",
                        "HOLDER:STOP_TERMINATED:SMS",
                        "VICTIM:STOP_TERMINATED:DOCUMENT",
                        "RELATED_BANK:STOP_TERMINATED:DOCUMENT");
    }

    @Test
    @DisplayName("접수와 보완 요청은 명의인 문자다")
    void receiptAndSupplement() {
        assertThat(targets(NoticePlan.receipt(1L, AT))).containsExactly("HOLDER:RECEIPT:SMS");
        assertThat(targets(NoticePlan.supplementRequest(1L, AT))).containsExactly("HOLDER:SUPPLEMENT_REQUEST:SMS");
    }

    private static List<String> targets(List<ObjectionNotice> notices) {
        return notices.stream()
                .map(n -> n.target() + ":" + n.kind() + ":" + n.channel())
                .toList();
    }
}
