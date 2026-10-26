package com.shinhan.infoetl.adapter.in.cli;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;

public final class EtlCliArguments {

    private static final String BASE_DATE_ARGUMENT = "--base-date=";
    private static final String FROM_ARGUMENT = "--from=";
    private static final String TO_ARGUMENT = "--to=";
    private static final String REBUILD_ARGUMENT = "--rebuild";
    private static final String AUTO_ARGUMENT = "--auto";
    private static final String VERIFY_PII_ARGUMENT = "--verify-pii";

    private EtlCliArguments() {}

    // --base-date= 하나, 또는 --from= 과 --to= 둘. 섞어 쓰면 거부한다.
    public static LoadCommand parseCommand(String[] args) {
        if (Arrays.asList(args).contains(VERIFY_PII_ARGUMENT)) {
            if (args.length != 1) {
                throw new IllegalArgumentException("--verify-pii는 다른 인자와 함께 쓸 수 없습니다.");
            }
            return LoadCommand.pii();
        }
        if (Arrays.asList(args).contains(AUTO_ARGUMENT)) {
            if (args.length != 1) {
                throw new IllegalArgumentException("--auto는 다른 인자와 함께 쓸 수 없습니다.");
            }
            return LoadCommand.automatic();
        }
        boolean hasBase = hasArgument(args, BASE_DATE_ARGUMENT);
        boolean hasFrom = hasArgument(args, FROM_ARGUMENT);
        boolean hasTo = hasArgument(args, TO_ARGUMENT);
        if (hasBase && (hasFrom || hasTo)) {
            throw new IllegalArgumentException("--base-date와 --from/--to는 함께 쓸 수 없습니다.");
        }
        boolean rebuild = Arrays.asList(args).contains(REBUILD_ARGUMENT);
        if (rebuild && !hasBase) {
            throw new IllegalArgumentException("--rebuild는 --base-date와 함께 써야 합니다.");
        }
        if (hasBase) {
            LocalDate baseDate = parseBaseDate(args);
            return new LoadCommand(baseDate, baseDate, false, rebuild, false, false);
        }
        if (hasFrom && hasTo) {
            LocalDate from = parseDate(args, FROM_ARGUMENT);
            LocalDate to = parseDate(args, TO_ARGUMENT);
            if (from.isAfter(to)) {
                throw new IllegalArgumentException("--from은 --to보다 늦을 수 없습니다.");
            }
            return new LoadCommand(from, to, true, false, false, false);
        }
        if (hasFrom || hasTo) {
            throw new IllegalArgumentException("--from과 --to는 함께 줘야 합니다.");
        }
        throw new IllegalArgumentException("--base-date=yyyy-MM-dd 또는 --from=yyyy-MM-dd --to=yyyy-MM-dd 인자가 필요합니다.");
    }

    public static LocalDate parseBaseDate(String[] args) {
        if (!hasArgument(args, BASE_DATE_ARGUMENT)) {
            throw new IllegalArgumentException("--base-date=yyyy-MM-dd 인자가 필요합니다.");
        }
        return parseDate(args, BASE_DATE_ARGUMENT);
    }

    private static boolean hasArgument(String[] args, String prefix) {
        for (String arg : args) {
            if (arg.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static LocalDate parseDate(String[] args, String prefix) {
        for (String arg : args) {
            if (arg.startsWith(prefix)) {
                try {
                    return LocalDate.parse(arg.substring(prefix.length()));
                } catch (DateTimeParseException e) {
                    throw new IllegalArgumentException(prefix + "는 yyyy-MM-dd 형식이어야 합니다.", e);
                }
            }
        }
        throw new IllegalArgumentException(prefix + " 인자가 필요합니다.");
    }
}
