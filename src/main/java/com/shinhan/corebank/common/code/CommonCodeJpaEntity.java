package com.shinhan.corebank.common.code;

import com.shinhan.corebank.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공통코드 (REQ-CMN-023).
 * 상품유형·거래구분·계좌상태·이체처리상태 등 코드성 데이터의 화면 표시명을 관리한다.
 *
 * ⚠️ code 값은 서버 Enum 값과 문자열이 정확히 일치해야 한다.
 *    Enum 에는 있는데 이 테이블에 행이 없으면 프론트가 표시명 매핑에 실패한다.
 *
 * P5 테이블 중 유일하게 created_at + updated_at 쌍을 모두 가지므로 BaseEntity 를 상속한다.
 */
@Entity
@Table(name = "common_code")
@IdClass(CommonCodeId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommonCodeJpaEntity extends BaseEntity {

    @Id
    @Column(name = "code_group", length = 50)
    private String codeGroup; // 예: AUTO_TRANSFER_STATUS

    @Id
    @Column(name = "code", length = 50)
    private String code; // 예: NORMAL

    @Column(name = "code_name", nullable = false, length = 100)
    private String codeName; // 예: 정상

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder; // 드롭다운 표시 순서

    @Column(name = "use_yn", nullable = false, columnDefinition = "CHAR(1)")
    private String useYn; // Y / N. 물리 삭제 대신 사용

    @Column(name = "description", length = 200)
    private String description; // 관리자용 설명

    public boolean isActive() {
        return "Y".equals(this.useYn);
    }
}
