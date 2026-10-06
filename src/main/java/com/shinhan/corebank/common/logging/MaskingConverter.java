package com.shinhan.corebank.common.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.pattern.CompositeConverter;
import com.shinhan.corebank.common.util.MaskingUtil;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MaskingConverter extends CompositeConverter<ILoggingEvent> {

    private static final Pattern ACCOUNT_NUMBER = Pattern.compile("\\b\\d{12}\\b");
    private static final Pattern PHONE_NUMBER = Pattern.compile("\\b\\d{11}\\b");

    @Override
    protected String transform(ILoggingEvent event, String in) {
        String masked = maskAll(ACCOUNT_NUMBER, in, MaskingUtil::maskAccountNumber);
        return maskAll(PHONE_NUMBER, masked, MaskingUtil::maskPhoneNumber);
    }

    // Matcher.appendReplacement는 교체 문자열의 '*'를 특수문자로 오인할 수 있어 직접 이어붙인다
    private static String maskAll(Pattern pattern, String input, Function<String, String> masker) {
        Matcher matcher = pattern.matcher(input);
        StringBuilder result = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            result.append(input, last, matcher.start());
            result.append(masker.apply(matcher.group()));
            last = matcher.end();
        }
        result.append(input, last, input.length());
        return result.toString();
    }
}
