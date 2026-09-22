package com.shinhan.corebank.transfer.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import com.shinhan.corebank.transfer.domain.TransferType;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TransferHistoryCsvWriterTest {

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    @Test
    @DisplayName("빈 목록이면 BOM과 헤더 행만 내려간다")
    void emptyList_headerOnly() {
        byte[] csv = TransferHistoryCsvWriter.write(List.of());

        String body = stripBom(csv);
        assertThat(body).isEqualTo("거래번호,처리상태,처리일시,입금계좌번호,예금주명,금액,이체구분,채널,오류코드,실패사유\r\n");
    }

    @Test
    @DisplayName("정상 건은 헤더 다음 줄에 필드 순서대로 값이 채워진다")
    void normalRow_rendersAllFields() {
        TransferHistoryItemResponse item = new TransferHistoryItemResponse(
                "20260810IT0000000012",
                ProcessResultStatus.SUCCESS,
                OffsetDateTime.parse("2026-08-10T09:00:00+09:00"),
                "110******222",
                "성*향",
                10000L,
                TransferType.IMMEDIATE,
                TransferChannel.WB,
                null,
                null);

        String body = stripBom(TransferHistoryCsvWriter.write(List.of(item)));
        String[] lines = body.split("\r\n");

        assertThat(lines).hasSize(2);
        // OffsetDateTime.toString()은 초가 0이면 생략한다("09:00:00" -> "09:00") — java.time 표준 동작
        assertThat(lines[1])
                .isEqualTo("20260810IT0000000012,SUCCESS,2026-08-10T09:00+09:00,110******222,성*향,10000,IMMEDIATE,WB,,");
    }

    @Test
    @DisplayName("실패 사유에 쉼표가 있으면 큰따옴표로 감싼다")
    void failureReasonWithComma_isQuoted() {
        TransferHistoryItemResponse item = new TransferHistoryItemResponse(
                "20260810IT0000000013",
                ProcessResultStatus.ERROR,
                OffsetDateTime.parse("2026-08-10T09:00:00+09:00"),
                "110******222",
                "성*향",
                10000L,
                TransferType.IMMEDIATE,
                TransferChannel.WB,
                "TRF0303",
                "잔액, 부족");

        String body = stripBom(TransferHistoryCsvWriter.write(List.of(item)));
        String[] lines = body.split("\r\n");

        assertThat(lines[1]).endsWith(",TRF0303,\"잔액, 부족\"");
    }

    @Test
    @DisplayName("값에 큰따옴표가 있으면 두 번 반복해서 이스케이핑한다")
    void valueWithQuote_isEscapedByDoubling() {
        TransferHistoryItemResponse item = new TransferHistoryItemResponse(
                "20260810IT0000000014",
                ProcessResultStatus.ERROR,
                OffsetDateTime.parse("2026-08-10T09:00:00+09:00"),
                "110******222",
                "성*향",
                10000L,
                TransferType.IMMEDIATE,
                TransferChannel.WB,
                "TRF0303",
                "사유 \"확인필요\"");

        String body = stripBom(TransferHistoryCsvWriter.write(List.of(item)));
        String[] lines = body.split("\r\n");

        assertThat(lines[1]).endsWith(",TRF0303,\"사유 \"\"확인필요\"\"\"");
    }

    @Test
    @DisplayName("파일 맨 앞에 UTF-8 BOM이 붙어 Excel에서 한글이 깨지지 않는다")
    void csv_startsWithUtf8Bom() {
        byte[] csv = TransferHistoryCsvWriter.write(List.of());

        assertThat(csv[0]).isEqualTo(UTF8_BOM[0]);
        assertThat(csv[1]).isEqualTo(UTF8_BOM[1]);
        assertThat(csv[2]).isEqualTo(UTF8_BOM[2]);
    }

    private String stripBom(byte[] csv) {
        return new String(csv, UTF8_BOM.length, csv.length - UTF8_BOM.length, StandardCharsets.UTF_8);
    }
}
