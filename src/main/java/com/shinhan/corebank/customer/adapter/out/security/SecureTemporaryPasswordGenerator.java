package com.shinhan.corebank.customer.adapter.out.security;

import com.shinhan.corebank.customer.application.port.out.TemporaryPasswordGeneratorPort;
import java.security.SecureRandom;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

// 대문자·소문자·숫자·특수문자를 한 글자 이상씩 넣은 12자 임시 비밀번호를 만든다.
// 관리자가 불러 주는 값이라 0/O·1/l/I처럼 헷갈리는 글자와 JSON·HTML에서 깨지는 글자는 뺀다.
// 셀프 재설정·비밀번호 변경의 금칙(같은 문자 4연속, 1씩 증감하는 숫자 4자리)에 걸리면 다시 만든다.
// 금칙은 auth의 LoginPasswordChangeService·PasswordResetService와 같게 맞춘다(규칙 2로 직접 참조하지 않음).
@Component
public class SecureTemporaryPasswordGenerator implements TemporaryPasswordGeneratorPort {

    private static final int LENGTH = 12;
    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnopqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String SPECIAL = "!@#$%^*-_+=?";
    private static final String ALL = UPPER + LOWER + DIGITS + SPECIAL;
    private static final Pattern FOUR_REPEATED_CHARACTERS = Pattern.compile("(.)\\1{3}");

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String generate() {
        String password;
        do {
            password = generateCandidate();
        } while (violatesRestrictions(password));
        return password;
    }

    private String generateCandidate() {
        char[] password = new char[LENGTH];
        password[0] = pick(UPPER);
        password[1] = pick(LOWER);
        password[2] = pick(DIGITS);
        password[3] = pick(SPECIAL);
        for (int i = 4; i < LENGTH; i++) {
            password[i] = pick(ALL);
        }
        shuffle(password);
        return new String(password);
    }

    static boolean violatesRestrictions(String password) {
        return FOUR_REPEATED_CHARACTERS.matcher(password).find() || containsFourConsecutiveDigits(password);
    }

    private static boolean containsFourConsecutiveDigits(String password) {
        for (int start = 0; start <= password.length() - 4; start++) {
            String value = password.substring(start, start + 4);
            if (value.chars().allMatch(Character::isDigit)) {
                int gap = value.charAt(1) - value.charAt(0);
                if ((gap == 1 || gap == -1)
                        && value.charAt(2) - value.charAt(1) == gap
                        && value.charAt(3) - value.charAt(2) == gap) {
                    return true;
                }
            }
        }
        return false;
    }

    private char pick(String characters) {
        return characters.charAt(secureRandom.nextInt(characters.length()));
    }

    // 필수 글자가 항상 앞 네 자리에 오지 않도록 Fisher-Yates로 섞는다.
    private void shuffle(char[] password) {
        for (int i = password.length - 1; i > 0; i--) {
            int j = secureRandom.nextInt(i + 1);
            char temp = password[i];
            password[i] = password[j];
            password[j] = temp;
        }
    }
}
