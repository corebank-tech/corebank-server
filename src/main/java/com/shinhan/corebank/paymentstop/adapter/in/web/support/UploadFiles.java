package com.shinhan.corebank.paymentstop.adapter.in.web.support;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.paymentstop.domain.document.DocumentPolicy;
import com.shinhan.corebank.paymentstop.domain.document.UploadFile;
import com.shinhan.corebank.paymentstop.domain.exception.PaymentStopErrorCode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import org.springframework.web.multipart.MultipartFile;

/** multipart 파일을 업로드 명령으로 바꾸고, 멱등 지문에 넣을 해시 목록을 만든다. 파일 본문은 지문에 넣지 않는다. */
public final class UploadFiles {

    private UploadFiles() {}

    /** 개수 범위 밖이면 PST0007. 형식과 크기는 서비스의 DocumentPolicy 가 본다. */
    public static List<UploadFile> of(List<MultipartFile> files, int min, int max) {
        List<MultipartFile> present = files == null
                ? List.of()
                : files.stream().filter(file -> file != null).toList();
        if (present.size() < min || present.size() > max) {
            throw new BusinessException(PaymentStopErrorCode.DOCUMENT_INVALID);
        }
        return present.stream().map(UploadFiles::of).toList();
    }

    public static UploadFile of(MultipartFile file) {
        if (file.getSize() > DocumentPolicy.MAX_FILE_BYTES) {
            throw new BusinessException(PaymentStopErrorCode.DOCUMENT_INVALID);
        }
        try {
            return new UploadFile(file.getOriginalFilename(), file.getBytes());
        } catch (IOException e) {
            throw new UncheckedIOException("업로드 파일을 읽지 못했습니다.", e);
        }
    }

    public static List<Map<String, Object>> fingerprint(List<UploadFile> files) {
        return files.stream()
                .map(file -> Map.<String, Object>of(
                        "name", String.valueOf(file.originalName()), "sha256", DocumentPolicy.sha256(file.content())))
                .toList();
    }
}
