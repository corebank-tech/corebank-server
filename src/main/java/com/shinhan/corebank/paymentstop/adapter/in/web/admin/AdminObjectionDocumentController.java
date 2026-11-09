package com.shinhan.corebank.paymentstop.adapter.in.web.admin;

import com.shinhan.corebank.auth.api.CurrentCustomerProvider;
import com.shinhan.corebank.common.audit.AccessReason;
import com.shinhan.corebank.common.idempotency.IdempotencyFingerprint;
import com.shinhan.corebank.common.idempotency.IdempotentRequestExecutor;
import com.shinhan.corebank.common.response.ApiResponse;
import com.shinhan.corebank.paymentstop.adapter.in.web.support.UploadFiles;
import com.shinhan.corebank.paymentstop.application.port.in.AdminActor;
import com.shinhan.corebank.paymentstop.application.port.in.DocumentUseCase;
import com.shinhan.corebank.paymentstop.application.port.in.DocumentUseCase.DocumentContent;
import com.shinhan.corebank.paymentstop.domain.document.DocumentKind;
import com.shinhan.corebank.paymentstop.domain.document.DocumentOcr;
import com.shinhan.corebank.paymentstop.domain.document.DocumentPolicy;
import com.shinhan.corebank.paymentstop.domain.document.OcrPage;
import com.shinhan.corebank.paymentstop.domain.document.UploadFile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Positive;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.type.TypeReference;

// 이의제기 첨부 서류(PA-12, PA-20). 영업점 대리 업로드와 담당자 열람. 인가는 SecurityConfig 의 /admin/objections 규칙이 맡는다.
@RestController
@RequestMapping("/admin/objections/{objectionId}/documents")
@RequiredArgsConstructor
@Validated
@Tag(name = "관리자 이의제기 서류", description = "서류 대리 업로드, 목록, 파일 열람(조회 감사), OCR 결과")
public class AdminObjectionDocumentController {

    private final DocumentUseCase documentUseCase;
    private final CurrentCustomerProvider currentCustomerProvider;
    private final IdempotentRequestExecutor idempotentRequestExecutor;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            operationId = "uploadObjectionDocuments",
            summary = "서류 대리 업로드",
            description = "files 1~10개, kind 는 EVIDENCE 또는 ID_COPY. PDF, PNG, JPEG 만 받고 형식은 매직 바이트로 본다. 파일 하나 10MB, "
                    + "이의제기당 합계 30MB, 10개까지. 신분증 사본은 암호화 저장한다. 오류: PST0007 형식, 크기, 개수 위반, PST0302 이미 확정. "
                    + "멱등키 필수.")
    public ResponseEntity<ApiResponse<List<DocumentResponse>>> upload(
            @PathVariable @Positive Long objectionId,
            @Parameter(description = "멱등키", required = true) @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestParam("kind") DocumentKind kind,
            @RequestParam(value = "files", required = false) List<MultipartFile> files,
            HttpServletRequest http) {
        AdminActor actor = new AdminActor(currentCustomerProvider.getCurrentCustomerId(), http.getRemoteAddr());
        List<UploadFile> uploads = UploadFiles.of(files, 1, DocumentPolicy.MAX_FILES);
        return idempotentRequestExecutor.execute(
                idempotencyKey,
                actor.adminCustomerId(),
                "POST /admin/objections/" + objectionId + "/documents",
                IdempotencyFingerprint.of(
                        actor.adminCustomerId(),
                        Map.of("kind", kind.name(), "files", UploadFiles.fingerprint(uploads)),
                        Map.of("objectionId", objectionId)),
                new TypeReference<>() {},
                () -> ApiResponse.success(documentUseCase.upload(objectionId, kind, uploads, actor).stream()
                        .map(DocumentResponse::from)
                        .toList()));
    }

    @GetMapping
    @Operation(
            operationId = "listObjectionDocuments",
            summary = "서류 목록",
            description = "신분증 사본도 kind=ID_COPY 로 보인다. 파일 본문은 내용 API 로 받는다.")
    public ApiResponse<List<DocumentResponse>> list(@PathVariable @Positive Long objectionId) {
        return ApiResponse.success(documentUseCase.list(objectionId).stream()
                .map(DocumentResponse::from)
                .toList());
    }

    @GetMapping("/{documentId}/content")
    @Operation(
            operationId = "getObjectionDocumentContent",
            summary = "서류 파일",
            description = "ApiResponse 가 아니라 파일 바이트를 내려받기(attachment)로 준다. 신분증 사본이 있을 수 있어 조회 사유 헤더를 받고 "
                    + "조회 감사를 남긴다. 오류: PST0205 문서 없음, CMN0007 조회 사유 없음.")
    public ResponseEntity<byte[]> content(
            @PathVariable @Positive Long objectionId,
            @PathVariable @Positive Long documentId,
            @Parameter(description = "조회 사유", example = "OPERATION") @RequestHeader(AccessReason.HEADER)
                    AccessReason accessReason,
            HttpServletRequest http) {
        AdminActor actor = new AdminActor(currentCustomerProvider.getCurrentCustomerId(), http.getRemoteAddr());
        DocumentContent content = documentUseCase.content(objectionId, documentId, actor, accessReason);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.document().contentType()))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(content.document().fileName(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(content.content());
    }

    @GetMapping("/{documentId}/ocr")
    @Operation(
            operationId = "getObjectionDocumentOcr",
            summary = "서류 OCR 결과",
            description = "텍스트는 가명 처리된 값이다. OCR 전이면 status=PENDING 이고 pages 가 비어 있다. 신분증 사본은 OCR 하지 않아 PST0205 다.")
    public ApiResponse<OcrResponse> ocr(
            @PathVariable @Positive Long objectionId, @PathVariable @Positive Long documentId) {
        return ApiResponse.success(OcrResponse.from(documentUseCase.ocr(objectionId, documentId)));
    }

    public record OcrResponse(
            long documentId, String status, String engine, List<OcrPage> pages, LocalDateTime updatedAt) {

        static OcrResponse from(DocumentOcr ocr) {
            return new OcrResponse(ocr.documentId(), ocr.status().name(), ocr.engine(), ocr.pages(), ocr.updatedAt());
        }
    }
}
