package com.shinhan.corebank.common.util;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

// REQ-CMN-019/POL-022 "5·10·20·30·50·전체" 페이지 크기 규칙을 목록 조회 API 전체가 공통으로 따른다.
// all=true면 size/page 검증 없이 Pageable.unpaged()를 반환, 조회 자체는 각 도메인이 Pagealbe을 받아서 그대로 자기 조회 메서드에 넘김
public class PageableResolver {
    public static Pageable resolve(int page, int size, boolean all, Set<Integer> allowedPageSizes) {
        if (all) {
            return Pageable.unpaged();
        }
        if (!allowedPageSizes.contains(size)) {
            throw new BusinessException(CommonErrorCode.INVALID_PAGE_SIZE);
        }
        if (page < 0) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "page는 0 이상이어야 합니다.");
        }
        return PageRequest.of(page, size);
    }

    private PageableResolver() {} // new 만드는거 방지
}
