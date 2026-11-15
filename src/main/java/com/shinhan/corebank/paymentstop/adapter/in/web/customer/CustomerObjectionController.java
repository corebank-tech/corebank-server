package com.shinhan.corebank.paymentstop.adapter.in.web.customer;

import com.shinhan.corebank.auth.api.CurrentCustomerProvider;
import com.shinhan.corebank.common.idempotency.IdempotencyFingerprint;
import com.shinhan.corebank.common.idempotency.IdempotentRequestExecutor;
import com.shinhan.corebank.common.response.ApiResponse;
import com.shinhan.corebank.paymentstop.adapter.in.web.support.UploadFiles;
import com.shinhan.corebank.paymentstop.application.port.in.CustomerObjectionUseCase;
import com.shinhan.corebank.paymentstop.application.port.in.CustomerObjectionUseCase.MyCase;
import com.shinhan.corebank.paymentstop.domain.document.ObjectionDocument;
import com.shinhan.corebank.paymentstop.domain.document.UploadFile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.type.TypeReference;

/**
 * 고객 비대면 이의제기(D34, PA-20). 로그인 고객 본인 계좌의 사건만 다룬다. 남의 사건과 없는 사건은 같은 오류로 거부한다. 계약은
 * docs/phase2/objection_admin_contract.md 의 고객 API 절.
 */
@RestController
@RequiredArgsConstructor
@Validated
@Tag(name = "지급정지 이의제기", description = "내 지급정지 사건 조회, 이의제기 신청, 보완 자료 제출")
public class CustomerObjectionController {

    private static final int MAX_EVIDENCE = 9;

    private final CustomerObjectionUseCase customerObjectionUseCase;
    private final CurrentCustomerProvider currentCustomerProvider;
    private final IdempotentRequestExecutor idempotentRequestExecutor;

    @GetMapping("/payment-stops/my")
    @Operation(
            operationId = "listMyPaymentStops",
            summary = "내 지급정지 사건",
            description = "로그인 고객 계좌에 걸린 지급정지 사건과 이의제기 진행. 보완 요청 중이면 요청 서류와 보완 기한이 보인다.")
    public ApiResponse<List<MyCaseResponse>> myCases() {
        return ApiResponse.success(
                customerObjectionUseCase.myCases(currentCustomerProvider.getCurrentCustomerId()).stream()
                        .map(MyCaseResponse::from)
                        .toList());
    }

    @PostMapping(value = "/payment-stops/{caseId}/objections", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            operationId = "submitMyObjection",
            summary = "이의제기 신청",
            description = "법정 서식(별지 제4호) 항목과 신분증 사본(필수 1개), 증빙(0~9개)을 함께 낸다. PDF, PNG, JPEG 만, 파일 하나 10MB, "
                    + "합계 30MB. 분석을 대기열에 넣고 접수 안내를 기록한다. 오류: PST0201 본인 계좌의 지급정지 사건이 아님, "
                    + "PST0002 신청 기한 경과, PST0003 신분증 사본이나 사유 없음, PST0007 파일 위반, PST0304 이미 신청함. 멱등키 필수.")
    public ResponseEntity<ApiResponse<SubmitResponse>> submit(
            @PathVariable @Positive Long caseId,
            @Parameter(description = "멱등키", required = true) @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestParam("clause") @NotNull @Min(1) @Max(3) Integer clause,
            @RequestParam(value = "statement", required = false) @Size(max = 4000) String statement,
            @RequestParam(value = "simplifiedRequested", defaultValue = "false") boolean simplifiedRequested,
            @RequestParam(value = "litigationPending", defaultValue = "false") boolean litigationPending,
            @RequestParam(value = "idCopy", required = false) MultipartFile idCopy,
            @RequestParam(value = "evidence", required = false) List<MultipartFile> evidence) {
        long customerId = currentCustomerProvider.getCurrentCustomerId();
        // 신분증 사본이 없으면 접수 요건 위반(PST0003)이다. 빈 파일은 파일 위반(PST0007)으로 본다
        UploadFile idCopyFile = idCopy == null ? null : UploadFiles.of(idCopy);
        List<UploadFile> evidenceFiles = UploadFiles.of(evidence, 0, MAX_EVIDENCE);

        Map<String, Object> fingerprint = new LinkedHashMap<>();
        fingerprint.put("clause", clause);
        fingerprint.put("statement", statement);
        fingerprint.put("simplifiedRequested", simplifiedRequested);
        fingerprint.put("litigationPending", litigationPending);
        fingerprint.put("idCopy", idCopyFile == null ? List.of() : UploadFiles.fingerprint(List.of(idCopyFile)));
        fingerprint.put("evidence", UploadFiles.fingerprint(evidenceFiles));

        return idempotentRequestExecutor.execute(
                idempotencyKey,
                customerId,
                "POST /payment-stops/" + caseId + "/objections",
                IdempotencyFingerprint.of(customerId, fingerprint, Map.of("caseId", caseId)),
                new TypeReference<>() {},
                () -> ApiResponse.success(new SubmitResponse(customerObjectionUseCase.submit(
                        customerId,
                        new CustomerObjectionUseCase.SubmitCommand(
                                caseId,
                                clause,
                                statement,
                                simplifiedRequested,
                                litigationPending,
                                idCopyFile,
                                evidenceFiles)))));
    }

    @PostMapping(value = "/objections/{objectionId}/supplements", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            operationId = "submitMyObjectionSupplement",
            summary = "보완 자료 제출",
            description = "보완 요청(SUPPLEMENT_REQUESTED) 상태에서만 받는다. 증빙 1~9개와 메모. 제출하면 보완 제출(SUPPLEMENT_SUBMITTED)이 되고 "
                    + "분석을 다시 대기열에 넣는다. 오류: PST0202 본인 이의제기가 아님, PST0007 파일 위반, PST0308 보완 요청 상태가 아님. 멱등키 필수.")
    public ResponseEntity<ApiResponse<List<MyDocumentResponse>>> submitSupplement(
            @PathVariable @Positive Long objectionId,
            @Parameter(description = "멱등키", required = true) @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestParam(value = "evidence", required = false) List<MultipartFile> evidence,
            @RequestParam(value = "note", required = false) @Size(max = 1000) String note) {
        long customerId = currentCustomerProvider.getCurrentCustomerId();
        List<UploadFile> files = UploadFiles.of(evidence, 1, MAX_EVIDENCE);
        Map<String, Object> fingerprint = new LinkedHashMap<>();
        fingerprint.put("note", note);
        fingerprint.put("evidence", UploadFiles.fingerprint(files));
        return idempotentRequestExecutor.execute(
                idempotencyKey,
                customerId,
                "POST /objections/" + objectionId + "/supplements",
                IdempotencyFingerprint.of(customerId, fingerprint, Map.of("objectionId", objectionId)),
                new TypeReference<>() {},
                () -> ApiResponse.success(
                        customerObjectionUseCase.submitSupplement(customerId, objectionId, files, note).stream()
                                .map(MyDocumentResponse::from)
                                .toList()));
    }

    public record SubmitResponse(long objectionId) {}

    /**
     * @param objectionId 아직 신청하지 않았으면 null
     * @param decisionResult 확정된 심사결과(ACCEPTED, PARTIAL, REJECTED). 확정 전이면 null
     */
    public record MyCaseResponse(
            long caseId,
            String accountNumberMasked,
            String caseStatus,
            long disputedTotal,
            LocalDate objectionDeadline,
            Long objectionId,
            String objectionStatus,
            LocalDate supplementDue,
            List<String> missingDocuments,
            String decisionResult) {

        static MyCaseResponse from(MyCase myCase) {
            return new MyCaseResponse(
                    myCase.caseId(),
                    myCase.accountNumberMasked(),
                    myCase.caseStatus(),
                    myCase.disputedTotal(),
                    myCase.objectionDeadline(),
                    myCase.objectionId(),
                    myCase.objectionStatus() == null
                            ? null
                            : myCase.objectionStatus().name(),
                    myCase.supplementDue(),
                    myCase.missingDocuments(),
                    myCase.decisionResult() == null
                            ? null
                            : myCase.decisionResult().name());
        }
    }

    public record MyDocumentResponse(
            long documentId,
            String kind,
            String fileName,
            String contentType,
            long size,
            int round,
            LocalDateTime uploadedAt) {

        static MyDocumentResponse from(ObjectionDocument document) {
            return new MyDocumentResponse(
                    document.documentId(),
                    document.kind().name(),
                    document.fileName(),
                    document.contentType(),
                    document.sizeBytes(),
                    document.round(),
                    document.uploadedAt());
        }
    }
}
