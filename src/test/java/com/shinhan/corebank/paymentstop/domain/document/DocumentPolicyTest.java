package com.shinhan.corebank.paymentstop.domain.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.paymentstop.domain.exception.PaymentStopErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("첨부 파일 검사(D36)")
class DocumentPolicyTest {

    static final byte[] PDF = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);

    @Test
    @DisplayName("형식은 이름이 아니라 매직 바이트로 정한다")
    void detectsByMagicBytes() {
        assertThat(DocumentPolicy.contentType(PDF)).isEqualTo("application/pdf");
        assertThat(DocumentPolicy.contentType(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0}))
                .isEqualTo("image/png");
        assertThat(DocumentPolicy.contentType(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0}))
                .isEqualTo("image/jpeg");
        assertInvalid(() -> DocumentPolicy.contentType("MZ fake exe".getBytes(StandardCharsets.US_ASCII)));
        assertInvalid(() -> DocumentPolicy.validate(
                List.of(new UploadFile("계약서.pdf", "<html>".getBytes(StandardCharsets.UTF_8))), 0, 0));
    }

    @Test
    @DisplayName("파일 하나 10MB, 합계 30MB, 개수 10개를 넘으면 PST0007이고 빈 파일도 받지 않는다")
    void enforcesLimits() {
        byte[] exact = sized(DocumentPolicy.MAX_FILE_BYTES);
        DocumentPolicy.validate(List.of(new UploadFile("a.pdf", exact)), 0, 0);

        assertInvalid(() -> DocumentPolicy.validate(
                List.of(new UploadFile("a.pdf", sized(DocumentPolicy.MAX_FILE_BYTES + 1))), 0, 0));
        assertInvalid(() -> DocumentPolicy.validate(
                List.of(new UploadFile("a.pdf", exact)), 2, DocumentPolicy.MAX_TOTAL_BYTES - exact.length + 1));
        assertInvalid(() -> DocumentPolicy.validate(Collections.nCopies(11, new UploadFile("a.pdf", PDF)), 0, 0));
        assertInvalid(() -> DocumentPolicy.validate(List.of(new UploadFile("a.pdf", PDF)), 10, PDF.length * 10L));
        assertInvalid(() -> DocumentPolicy.validate(List.of(new UploadFile("a.pdf", new byte[0])), 0, 0));
    }

    @Test
    @DisplayName("파일 이름에서 경로와 제어 문자를 떼고, SHA-256 은 16진수 64자다")
    void sanitizesNameAndHashes() {
        assertThat(DocumentPolicy.fileName("../../etc/passwd")).isEqualTo("passwd");
        assertThat(DocumentPolicy.fileName("C:\\users\\계약서\n.pdf")).isEqualTo("계약서.pdf");
        assertThat(DocumentPolicy.fileName(null)).isEqualTo("document");
        assertThat(DocumentPolicy.sha256(PDF)).hasSize(64).matches("[0-9a-f]+");
    }

    private static byte[] sized(long size) {
        byte[] content = new byte[(int) size];
        System.arraycopy(PDF, 0, content, 0, PDF.length);
        return content;
    }

    private static void assertInvalid(ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentStopErrorCode.DOCUMENT_INVALID);
    }
}
