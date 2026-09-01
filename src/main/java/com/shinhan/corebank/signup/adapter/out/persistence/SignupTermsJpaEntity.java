package com.shinhan.corebank.signup.adapter.out.persistence;

import com.shinhan.corebank.common.entity.BaseEntity;
import com.shinhan.corebank.signup.domain.model.TermsType;
import jakarta.persistence.*;

// terms 테이블의 회원가입 약관 필드를 JPA로 매핑한다.
@Entity
@Table(name = "terms")
public class SignupTermsJpaEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "terms_id")
    private Long termsId;

    @Column(name = "terms_code", nullable = false, length = 30)
    private String termsCode;

    @Column(nullable = false, length = 10)
    private String version;

    @Enumerated(EnumType.STRING)
    @Column(name = "terms_type", nullable = false, length = 20)
    private TermsType termsType;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "is_required", nullable = false)
    private boolean required;

    @Column(name = "view_required", nullable = false)
    private boolean viewRequired;

    protected SignupTermsJpaEntity() {}

    public Long getTermsId() {
        return termsId;
    }

    public String getTermsCode() {
        return termsCode;
    }

    public String getVersion() {
        return version;
    }

    public TermsType getTermsType() {
        return termsType;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public boolean isRequired() {
        return required;
    }

    public boolean isViewRequired() {
        return viewRequired;
    }
}
