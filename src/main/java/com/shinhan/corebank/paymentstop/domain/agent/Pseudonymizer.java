package com.shinhan.corebank.paymentstop.domain.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * LLM 입력과 단계 이벤트에 원문 개인정보가 남지 않게 가린다(Plan 전역 제약, agent_run_step_schema §1).
 *
 * <p>주민등록번호, 연락처, 이메일, 계좌번호 형태를 자리표시로 바꾼다. 이름은 형태로 가릴 수 없어 대상이 아니다. 소명에는 이름을 받지
 * 않고, 원문 대조는 담당자 화면에서 원본으로 한다.
 */
public final class Pseudonymizer {

    // 순서가 중요하다. 주민등록번호와 연락처를 계좌번호보다 먼저 가린다(둘 다 숫자열이다)
    private static final List<Rule> RULES = List.of(
            new Rule(Pattern.compile("(?<![0-9A-Za-z])\\d{6}\\s*-\\s*[1-8]\\d{6}(?![0-9A-Za-z])"), "[주민등록번호]"),
            new Rule(Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"), "[이메일]"),
            new Rule(
                    Pattern.compile("(?<![0-9A-Za-z])01[016789]\\s*-?\\s*\\d{3,4}\\s*-?\\s*\\d{4}(?![0-9A-Za-z])"),
                    "[연락처]"),
            // 날짜(2026-11-05)는 마지막 묶음이 짧아 걸리지 않는다
            new Rule(
                    Pattern.compile("(?<![0-9A-Za-z])\\d{3,6}-\\d{2,6}-\\d{4,8}(-\\d{1,6})?(?![0-9A-Za-z])"), "[계좌번호]"),
            // 거래번호(20991231WB0000123456)의 숫자 꼬리는 앞이 영문이라 걸리지 않는다
            new Rule(Pattern.compile("(?<![0-9A-Za-z])\\d{10,14}(?![0-9A-Za-z])"), "[계좌번호]"));

    private Pseudonymizer() {}

    public static String mask(String text) {
        if (text == null) {
            return null;
        }
        String masked = text;
        for (Rule rule : RULES) {
            masked = rule.pattern().matcher(masked).replaceAll(rule.placeholder());
        }
        return masked;
    }

    /** JSON 으로 저장할 상세의 문자열 값을 모두 가린다. 키와 숫자는 그대로 둔다. */
    @SuppressWarnings("unchecked")
    public static Object maskDeep(Object value) {
        if (value instanceof String text) {
            return mask(text);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> masked = new LinkedHashMap<>();
            map.forEach((key, child) -> masked.put(String.valueOf(key), maskDeep(child)));
            return masked;
        }
        if (value instanceof List<?> list) {
            List<Object> masked = new ArrayList<>(list.size());
            list.forEach(child -> masked.add(maskDeep(child)));
            return masked;
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> maskMap(Map<String, Object> map) {
        return map == null ? null : (Map<String, Object>) maskDeep(map);
    }

    private record Rule(Pattern pattern, String placeholder) {}
}
