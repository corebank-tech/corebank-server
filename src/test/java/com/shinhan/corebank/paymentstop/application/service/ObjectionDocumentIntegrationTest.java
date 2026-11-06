package com.shinhan.corebank.paymentstop.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.support.CustomerTestFixture;
import com.shinhan.corebank.common.audit.AccessReason;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.ErrorCode;
import com.shinhan.corebank.paymentstop.application.port.in.AdminActor;
import com.shinhan.corebank.paymentstop.application.port.in.AgentWorkerUseCase;
import com.shinhan.corebank.paymentstop.application.port.in.AgentWorkerUseCase.ClaimedJob;
import com.shinhan.corebank.paymentstop.application.port.in.AgentWorkerUseCase.Context;
import com.shinhan.corebank.paymentstop.application.port.in.AgentWorkerUseCase.OcrCommand;
import com.shinhan.corebank.paymentstop.application.port.in.DocumentUseCase;
import com.shinhan.corebank.paymentstop.application.port.in.ExternalCheckUseCase;
import com.shinhan.corebank.paymentstop.application.port.in.ReceiveObjectionUseCase;
import com.shinhan.corebank.paymentstop.application.port.in.RegisterPaymentStopCaseUseCase;
import com.shinhan.corebank.paymentstop.domain.document.DocumentKind;
import com.shinhan.corebank.paymentstop.domain.document.ObjectionDocument;
import com.shinhan.corebank.paymentstop.domain.document.OcrPage;
import com.shinhan.corebank.paymentstop.domain.document.OcrStatus;
import com.shinhan.corebank.paymentstop.domain.document.UploadFile;
import com.shinhan.corebank.paymentstop.domain.exception.PaymentStopErrorCode;
import com.shinhan.corebank.paymentstop.domain.external.ExternalAgency;
import com.shinhan.corebank.paymentstop.domain.external.ExternalCheckReply;
import com.shinhan.corebank.paymentstop.support.PaymentStopTestData;
import com.shinhan.corebank.paymentstop.support.PaymentStopTestData.Scenario;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;

/** 첨부 서류 저장, 신분증 사본 보호(D37), 워커 OCR 저장과 가명 처리. 실제 파일을 테스트 저장소 루트에 쓴다. */
@DisplayName("이의제기 첨부 서류와 OCR 통합 테스트")
class ObjectionDocumentIntegrationTest extends IntegrationTestSupport {

    static final byte[] PDF = "%PDF-1.7\n계약서 본문\n".getBytes(StandardCharsets.UTF_8);
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4, 5, 6, 7, 8};

    @Autowired
    private DocumentUseCase documentUseCase;

    @Autowired
    private AgentWorkerUseCase worker;

    @Autowired
    private ExternalCheckUseCase externalCheckUseCase;

    @Autowired
    private RegisterPaymentStopCaseUseCase registerUseCase;

    @Autowired
    private ReceiveObjectionUseCase receiveUseCase;

    @Autowired
    private CustomerTestFixture customerTestFixture;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    @Value("${app.payment-stop.agent.worker-key}")
    private String workerKey;

    @Value("${app.payment-stop.documents.root}")
    private String storageRoot;

    private Long customerId;
    private AdminActor actor;
    private PaymentStopTestData data;

    @BeforeEach
    void setUp() {
        customerId = customerTestFixture.createCustomer();
        actor = new AdminActor(customerId, "127.0.0.1");
        data = new PaymentStopTestData(
                jdbcTemplate, customerId, LocalDateTime.now(clock).minusDays(1).withNano(0));
    }

    @AfterEach
    void tearDown() {
        data.cleanup();
        customerTestFixture.deleteCustomer(customerId);
    }

    @Test
    @DisplayName("매직 바이트가 틀리거나, 10MB를 넘거나, 10개를 넘으면 PST0007이고 아무것도 저장하지 않는다")
    void rejectsInvalidUploads() {
        long objectionId = receive(data.scenario(true));

        assertInvalid(() -> upload(
                objectionId,
                DocumentKind.EVIDENCE,
                new UploadFile("계약서.pdf", "<html>".getBytes(StandardCharsets.UTF_8))));
        byte[] tooBig = new byte[10 * 1024 * 1024 + 1];
        System.arraycopy(PDF, 0, tooBig, 0, PDF.length);
        assertInvalid(() -> upload(objectionId, DocumentKind.EVIDENCE, new UploadFile("big.pdf", tooBig)));
        assertInvalid(() -> documentUseCase.upload(
                objectionId, DocumentKind.EVIDENCE, Collections.nCopies(11, new UploadFile("a.pdf", PDF)), actor));

        documentUseCase.upload(
                objectionId, DocumentKind.EVIDENCE, Collections.nCopies(10, new UploadFile("a.pdf", PDF)), actor);
        assertInvalid(() -> upload(objectionId, DocumentKind.EVIDENCE, new UploadFile("one-more.png", PNG)));
        assertThat(documentUseCase.list(objectionId)).hasSize(10);
    }

    @Test
    @DisplayName("신분증 사본은 암호화해 저장하고 관리자 열람 때만 복호화하며 조회 감사를 남긴다. 증빙은 평문이고 해시를 남긴다")
    void idCopyEncryptedAtRest() throws Exception {
        long objectionId = receive(data.scenario(true));
        ObjectionDocument idCopy = upload(objectionId, DocumentKind.ID_COPY, new UploadFile("신분증.png", PNG));
        ObjectionDocument evidence = upload(objectionId, DocumentKind.EVIDENCE, new UploadFile("../계약서.pdf", PDF));

        byte[] storedIdCopy = Files.readAllBytes(Path.of(storageRoot).resolve(idCopy.storageKey()));
        assertThat(idCopy.encrypted()).isTrue();
        assertThat(storedIdCopy).isNotEqualTo(PNG);
        assertThat(new String(storedIdCopy, StandardCharsets.ISO_8859_1)).doesNotContain("PNG");
        assertThat(Files.readAllBytes(Path.of(storageRoot).resolve(evidence.storageKey())))
                .isEqualTo(PDF);
        assertThat(evidence.fileName()).isEqualTo("계약서.pdf");
        assertThat(evidence.sha256())
                .isEqualTo(com.shinhan.corebank.paymentstop.domain.document.DocumentPolicy.sha256(PDF));

        DocumentUseCase.DocumentContent content =
                documentUseCase.content(objectionId, idCopy.documentId(), actor, AccessReason.OPERATION);
        assertThat(content.content()).isEqualTo(PNG);
        assertThat(data.auditCount("OBJECTION_DOCUMENT_VIEW")).isEqualTo(1L);
        assertThat(documentUseCase.list(objectionId))
                .extracting(ObjectionDocument::kind, ObjectionDocument::ocrStatus)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(DocumentKind.ID_COPY, null),
                        org.assertj.core.groups.Tuple.tuple(DocumentKind.EVIDENCE, OcrStatus.PENDING));
        assertNotFound(() -> documentUseCase.ocr(objectionId, idCopy.documentId()));
    }

    @Test
    @DisplayName("워커에게 신분증 사본은 목록, 내용, OCR 저장 어디에도 보이지 않는다(D37)")
    void idCopyHiddenFromWorker() {
        long objectionId = receive(data.scenario(true));
        ObjectionDocument idCopy = upload(objectionId, DocumentKind.ID_COPY, new UploadFile("신분증.png", PNG));
        ObjectionDocument evidence = upload(objectionId, DocumentKind.EVIDENCE, new UploadFile("계약서.pdf", PDF));
        ClaimedJob job = claimOwn(objectionId);

        assertThat(worker.documents(job.jobToken(), objectionId))
                .extracting(AgentWorkerUseCase.WorkerDocument::documentId)
                .containsExactly(evidence.documentId());
        assertThat(worker.documentContent(job.jobToken(), objectionId, evidence.documentId())
                        .content())
                .isEqualTo(PDF);
        assertNotFound(() -> worker.documentContent(job.jobToken(), objectionId, idCopy.documentId()));
        assertNotFound(() -> worker.saveOcr(
                job.jobToken(), objectionId, idCopy.documentId(), new OcrCommand("paddle", null, List.of())));
        assertThat(worker.context(job.jobToken(), objectionId).documents())
                .extracting(AgentWorkerUseCase.ContextDocument::documentId)
                .containsExactly(evidence.documentId());
    }

    @Test
    @DisplayName("워커가 보낸 OCR 원문은 가명 처리해 저장하고, OCR 이 끝난 증빙만 context 에 글자 영역이 실린다")
    void workerOcrIsPseudonymized() {
        long objectionId = receive(data.scenario(true));
        ObjectionDocument evidence = upload(objectionId, DocumentKind.EVIDENCE, new UploadFile("계약서.pdf", PDF));
        externalCheckUseCase.request(objectionId, ExternalAgency.POLICE, ExternalCheckReply.NO_RECORD, actor);
        ClaimedJob job = claimOwn(objectionId);

        Context before = worker.context(job.jobToken(), objectionId);
        assertThat(before.documents().getFirst().pages()).isEmpty();
        assertThat(before.externalChecks())
                .extracting(AgentWorkerUseCase.WorkerExternalCheck::reply)
                .containsExactly(ExternalCheckReply.NO_RECORD);

        List<OcrPage> raw = List.of(new OcrPage(
                1,
                1240,
                1754,
                List.of(
                        new OcrPage.OcrRegion(0, "연락처 010-1234-5678", List.of(10.0, 20.0, 300.0, 40.0), 0.97),
                        new OcrPage.OcrRegion(1, "계좌 110-123-456789 로 입금", List.of(10.0, 80.0, 300.0, 40.0), 0.81))));
        List<OcrPage> saved = worker.saveOcr(
                job.jobToken(), objectionId, evidence.documentId(), new OcrCommand("paddleocr-2.7", null, raw));

        assertThat(saved.getFirst().regions())
                .extracting(OcrPage.OcrRegion::text)
                .containsExactly("연락처 [연락처]", "계좌 [계좌번호] 로 입금");
        assertThat(saved.getFirst().regions().get(1).bbox()).containsExactly(10.0, 80.0, 300.0, 40.0);
        String storedJson = jdbcTemplate.queryForObject(
                "SELECT CAST(pages AS CHAR) FROM objection_document_ocr WHERE document_id = ?",
                String.class,
                evidence.documentId());
        assertThat(storedJson).doesNotContain("010-1234-5678").doesNotContain("110-123-456789");
        assertThat(documentUseCase.ocr(objectionId, evidence.documentId()).status())
                .isEqualTo(OcrStatus.DONE);

        Context after = worker.context(job.jobToken(), objectionId);
        assertThat(after.documents().getFirst().ocrStatus()).isEqualTo(OcrStatus.DONE);
        assertThat(after.documents().getFirst().pages().getFirst().regions()).hasSize(2);
    }

    @Test
    @DisplayName("다른 이의제기의 문서는 그 이의제기 경로로 조회할 수 없다")
    void documentScopedToObjection() {
        long objectionId = receive(data.scenario(true));
        long otherId = receive(data.scenario(true));
        ObjectionDocument other = upload(otherId, DocumentKind.EVIDENCE, new UploadFile("계약서.pdf", PDF));

        assertNotFound(() -> documentUseCase.content(objectionId, other.documentId(), actor, AccessReason.OPERATION));
        assertNotFound(() -> documentUseCase.ocr(objectionId, other.documentId()));
    }

    private ObjectionDocument upload(long objectionId, DocumentKind kind, UploadFile file) {
        return documentUseCase.upload(objectionId, kind, List.of(file), actor).getFirst();
    }

    private ClaimedJob claimOwn(long objectionId) {
        return worker.claim(workerKey)
                .filter(job -> job.objectionId() == objectionId)
                .orElseThrow(() -> new AssertionError("이 테스트의 분석을 가져가지 못했습니다"));
    }

    private long receive(Scenario s) {
        long caseId = registerUseCase.register(
                new RegisterPaymentStopCaseUseCase.Command(
                        s.target(), List.of(s.disputed()), LocalDate.now(clock).plusDays(30)),
                actor);
        return receiveUseCase.receive(
                new ReceiveObjectionUseCase.Command(caseId, 1, "판매 대금으로 받은 돈입니다.", false, true, false), actor);
    }

    private static void assertInvalid(ThrowingCallable call) {
        assertErrorCode(call, PaymentStopErrorCode.DOCUMENT_INVALID);
    }

    private static void assertNotFound(ThrowingCallable call) {
        assertErrorCode(call, PaymentStopErrorCode.DOCUMENT_NOT_FOUND);
    }

    private static void assertErrorCode(ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(expected);
    }
}
