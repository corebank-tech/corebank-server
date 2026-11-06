package com.shinhan.corebank.paymentstop.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.support.CustomerTestFixture;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.ErrorCode;
import com.shinhan.corebank.paymentstop.application.port.in.AdminActor;
import com.shinhan.corebank.paymentstop.application.port.in.CustomerObjectionUseCase;
import com.shinhan.corebank.paymentstop.application.port.in.CustomerObjectionUseCase.MyCase;
import com.shinhan.corebank.paymentstop.application.port.in.CustomerObjectionUseCase.SubmitCommand;
import com.shinhan.corebank.paymentstop.application.port.in.RegisterPaymentStopCaseUseCase;
import com.shinhan.corebank.paymentstop.application.port.in.ReviewProgressUseCase;
import com.shinhan.corebank.paymentstop.domain.ObjectionStatus;
import com.shinhan.corebank.paymentstop.domain.document.UploadFile;
import com.shinhan.corebank.paymentstop.domain.exception.PaymentStopErrorCode;
import com.shinhan.corebank.paymentstop.support.PaymentStopTestData;
import com.shinhan.corebank.paymentstop.support.PaymentStopTestData.Scenario;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** 고객 비대면 이의제기(D34). 명의인은 사건 계좌의 주인이고, 관리자와 다른 고객은 따로 만든다. */
@DisplayName("고객 이의제기 신청과 보완 제출 통합 테스트")
class CustomerObjectionIntegrationTest extends IntegrationTestSupport {

    static final byte[] PDF = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);
    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10};

    @Autowired
    private CustomerObjectionUseCase customerUseCase;

    @Autowired
    private RegisterPaymentStopCaseUseCase registerUseCase;

    @Autowired
    private ReviewProgressUseCase progressUseCase;

    @Autowired
    private CustomerTestFixture customerTestFixture;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    private Long holderId;
    private Long adminId;
    private Long otherId;
    private AdminActor admin;
    private PaymentStopTestData data;

    @BeforeEach
    void setUp() {
        holderId = customerTestFixture.createCustomer();
        adminId = customerTestFixture.createCustomer();
        otherId = customerTestFixture.createCustomer();
        admin = new AdminActor(adminId, "127.0.0.1");
        data = new PaymentStopTestData(
                jdbcTemplate, holderId, LocalDateTime.now(clock).minusDays(1).withNano(0));
    }

    @AfterEach
    void tearDown() {
        data.cleanup();
        data.cleanupActor(adminId);
        data.cleanupActor(otherId);
        customerTestFixture.deleteCustomer(holderId);
        customerTestFixture.deleteCustomer(adminId);
        customerTestFixture.deleteCustomer(otherId);
    }

    @Test
    @DisplayName("명의인이 신청하면 접수, 분석 1건, 접수 통지, 신분증 사본(암호화)과 증빙이 함께 남고 내 사건 목록에 보인다")
    void submitRecordsEverything() {
        Scenario s = data.scenario(true);
        long caseId = register(s);

        assertThat(customerUseCase.myCases(holderId)).singleElement().satisfies(myCase -> {
            assertThat(myCase.caseId()).isEqualTo(caseId);
            assertThat(myCase.objectionId()).isNull();
            assertThat(myCase.accountNumberMasked()).contains("*");
        });

        long objectionId = customerUseCase.submit(holderId, command(caseId, List.of(new UploadFile("계약서.pdf", PDF))));

        assertThat(count("SELECT COUNT(*) FROM agent_run WHERE objection_id = ?", objectionId))
                .isEqualTo(1L);
        assertThat(data.notices(objectionId)).containsExactly("HOLDER:RECEIPT");
        assertThat(jdbcTemplate.queryForList(
                        "SELECT CONCAT(kind, ':', encrypted, ':', uploader_role) FROM objection_document"
                                + " WHERE objection_id = ? ORDER BY document_id",
                        String.class,
                        objectionId))
                .containsExactly("ID_COPY:1:HOLDER", "EVIDENCE:0:HOLDER");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT CONCAT(received_channel, ':', received_by) FROM objection WHERE objection_id = ?",
                        String.class,
                        objectionId))
                .isEqualTo("ONLINE:" + holderId);
        MyCase myCase = customerUseCase.myCases(holderId).getFirst();
        assertThat(myCase.objectionId()).isEqualTo(objectionId);
        assertThat(myCase.objectionStatus()).isEqualTo(ObjectionStatus.RECEIVED);
        assertThat(myCase.supplementDue()).isNull();
    }

    @Test
    @DisplayName("남의 사건과 없는 사건은 같은 PST0201로 거부하고 아무것도 남기지 않는다(존재 여부 비노출)")
    void otherCustomersCaseLooksMissing() {
        long caseId = register(data.scenario(true));

        assertErrorCode(
                () -> customerUseCase.submit(otherId, command(caseId, List.of())), PaymentStopErrorCode.CASE_NOT_FOUND);
        assertErrorCode(
                () -> customerUseCase.submit(otherId, command(Long.MAX_VALUE, List.of())),
                PaymentStopErrorCode.CASE_NOT_FOUND);
        assertThat(customerUseCase.myCases(otherId)).isEmpty();
        assertThat(count("SELECT COUNT(*) FROM objection WHERE case_id = ?", caseId))
                .isZero();
    }

    @Test
    @DisplayName("신분증 사본이 없으면 PST0003, 파일 형식이 틀리면 PST0007이고 접수가 남지 않는다")
    void requiresIdCopyAndValidFiles() {
        long caseId = register(data.scenario(true));

        assertErrorCode(
                () -> customerUseCase.submit(
                        holderId, new SubmitCommand(caseId, 1, "판매 대금입니다.", false, false, null, List.of())),
                PaymentStopErrorCode.OBJECTION_REQUIREMENTS_MISSING);
        assertErrorCode(
                () -> customerUseCase.submit(
                        holderId,
                        command(caseId, List.of(new UploadFile("a.pdf", "not pdf".getBytes(StandardCharsets.UTF_8))))),
                PaymentStopErrorCode.DOCUMENT_INVALID);
        assertThat(count("SELECT COUNT(*) FROM objection WHERE case_id = ?", caseId))
                .isZero();
    }

    @Test
    @DisplayName("보완은 요청, 제출, 완비 재확인을 두 번까지 돌고 세 번째 요청은 PST0006이다. 제출마다 분석이 1건씩 들어간다")
    void supplementRounds() {
        long caseId = register(data.scenario(true));
        long objectionId = customerUseCase.submit(holderId, command(caseId, List.of()));

        assertErrorCode(
                () -> customerUseCase.submitSupplement(holderId, objectionId, evidence(), null),
                PaymentStopErrorCode.INVALID_OBJECTION_STATE);

        ReviewProgressUseCase.SupplementResult first =
                progressUseCase.requestSupplement(objectionId, "계약서가 필요합니다", List.of("물품 계약서"), admin);
        MyCase awaiting = customerUseCase.myCases(holderId).getFirst();
        assertThat(awaiting.objectionStatus()).isEqualTo(ObjectionStatus.SUPPLEMENT_REQUESTED);
        assertThat(awaiting.supplementDue()).isEqualTo(first.supplementDue());
        assertThat(awaiting.missingDocuments()).containsExactly("물품 계약서");
        assertErrorCode(
                () -> customerUseCase.submitSupplement(otherId, objectionId, evidence(), null),
                PaymentStopErrorCode.OBJECTION_NOT_FOUND);

        assertThat(customerUseCase.submitSupplement(holderId, objectionId, evidence(), "계약서 첨부합니다"))
                .singleElement()
                .satisfies(document -> assertThat(document.round()).isEqualTo(1));
        assertThat(data.objectionStatus(objectionId)).isEqualTo("SUPPLEMENT_SUBMITTED");
        assertThat(customerUseCase.myCases(holderId).getFirst().missingDocuments())
                .isEmpty();

        progressUseCase.confirmCompleteness(objectionId, admin);
        ReviewProgressUseCase.SupplementResult second =
                progressUseCase.requestSupplement(objectionId, "거래내역서가 필요합니다", List.of("거래내역서"), admin);
        assertThat(second.round()).isEqualTo(2);
        assertThat(second.supplementDue()).isBefore(first.supplementDue().plusDays(14));
        customerUseCase.submitSupplement(holderId, objectionId, evidence(), null);

        assertErrorCode(
                () -> progressUseCase.requestSupplement(objectionId, "또", List.of(), admin),
                PaymentStopErrorCode.SUPPLEMENT_LIMIT_EXCEEDED);
        assertThat(count("SELECT COUNT(*) FROM agent_run WHERE objection_id = ?", objectionId))
                .isEqualTo(3L);
        assertThat(jdbcTemplate.queryForList(
                        "SELECT CONCAT(round, ':', submitted_at IS NOT NULL) FROM objection_supplement_request"
                                + " WHERE objection_id = ? ORDER BY round",
                        String.class,
                        objectionId))
                .containsExactly("1:1", "2:1");
    }

    private SubmitCommand command(long caseId, List<UploadFile> evidence) {
        return new SubmitCommand(
                caseId, 1, "판매 대금으로 받은 돈입니다.", false, false, new UploadFile("신분증.jpg", JPEG), evidence);
    }

    private static List<UploadFile> evidence() {
        return List.of(new UploadFile("보완.pdf", PDF));
    }

    private long register(Scenario s) {
        return registerUseCase.register(
                new RegisterPaymentStopCaseUseCase.Command(
                        s.target(), List.of(s.disputed()), LocalDate.now(clock).plusDays(30)),
                admin);
    }

    private long count(String sql, long id) {
        return jdbcTemplate.queryForObject(sql, Long.class, id);
    }

    private static void assertErrorCode(ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(expected);
    }
}
