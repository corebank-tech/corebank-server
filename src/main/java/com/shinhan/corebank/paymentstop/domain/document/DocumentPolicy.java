package com.shinhan.corebank.paymentstop.domain.document;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.paymentstop.domain.exception.PaymentStopErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * 첨부 파일 검사(D36, contract §5). PDF, PNG, JPEG 만 받고 형식은 매직 바이트로 판정한다. 파일 하나 10MB, 이의제기당 합계 30MB와 10개까지다.
 * 위반은 모두 PST0007 이다.
 */
public final class DocumentPolicy {

    public static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
    public static final long MAX_TOTAL_BYTES = 30L * 1024 * 1024;
    public static final int MAX_FILES = 10;
    private static final int MAX_FILE_NAME = 255;

    private static final byte[] PDF = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    private DocumentPolicy() {}

    /**
     * 새 파일을 더해도 한도 안인지 본다.
     *
     * @param existingCount 이 이의제기에 이미 있는 파일 수
     * @param existingBytes 이 이의제기에 이미 있는 파일 크기 합
     */
    public static void validate(List<UploadFile> files, int existingCount, long existingBytes) {
        if (files == null || files.isEmpty() || existingCount + files.size() > MAX_FILES) {
            throw invalid();
        }
        long total = existingBytes;
        for (UploadFile file : files) {
            byte[] content = file.content();
            if (content == null || content.length == 0 || content.length > MAX_FILE_BYTES) {
                throw invalid();
            }
            contentType(content);
            total += content.length;
        }
        if (total > MAX_TOTAL_BYTES) {
            throw invalid();
        }
    }

    /** 매직 바이트로 판정한 형식. 셋 다 아니면 PST0007. */
    public static String contentType(byte[] content) {
        if (startsWith(content, PDF)) {
            return "application/pdf";
        }
        if (startsWith(content, PNG)) {
            return "image/png";
        }
        if (startsWith(content, JPEG)) {
            return "image/jpeg";
        }
        throw invalid();
    }

    /** 경로 구분자 앞을 떼고 제어 문자를 지운 이름. 비면 "document" 다. */
    public static String fileName(String originalName) {
        String name = originalName == null ? "" : originalName;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        name = name.substring(slash + 1).replaceAll("\\p{Cntrl}", "").strip();
        if (name.isEmpty()) {
            name = "document";
        }
        return name.length() > MAX_FILE_NAME ? name.substring(name.length() - MAX_FILE_NAME) : name;
    }

    public static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 을 쓸 수 없습니다.", e);
        }
    }

    private static boolean startsWith(byte[] content, byte[] prefix) {
        if (content.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (content[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static BusinessException invalid() {
        return new BusinessException(PaymentStopErrorCode.DOCUMENT_INVALID);
    }
}
