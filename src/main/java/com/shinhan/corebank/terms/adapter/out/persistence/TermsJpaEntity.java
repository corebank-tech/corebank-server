package com.shinhan.corebank.terms.adapter.out.persistence;

import com.shinhan.corebank.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "terms")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TermsJpaEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "terms_id")
    private Long termsId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 10)
    private String version;

    @Column(name = "is_required", nullable = false)
    private boolean required;

    @Column(name = "view_required", nullable = false)
    private boolean viewRequired;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;
}
