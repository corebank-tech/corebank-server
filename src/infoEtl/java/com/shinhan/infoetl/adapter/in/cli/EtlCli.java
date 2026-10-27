package com.shinhan.infoetl.adapter.in.cli;

import com.shinhan.infoetl.application.port.in.AutoLoadUseCase;
import com.shinhan.infoetl.application.port.in.DailyLoadUseCase;
import com.shinhan.infoetl.application.port.in.PiiVerifyUseCase;
import com.shinhan.infoetl.application.port.in.RangeLoadUseCase;
import com.shinhan.infoetl.domain.LoadResult;
import com.shinhan.infoetl.domain.PiiVerificationResult;
import com.shinhan.infoetl.domain.RangeLoadResult;

// 명령 한 건을 입구 포트로 실행하고 결과를 로그와 종료 코드로 알린다. 서비스와 어댑터는 모른다.
public final class EtlCli {

    public static final int EXIT_OK = 0;
    public static final int EXIT_LOAD_FAILED = 1;
    public static final int EXIT_USAGE = 2;

    private static final System.Logger LOG = System.getLogger(EtlCli.class.getName());

    private final DailyLoadUseCase dailyLoad;
    private final RangeLoadUseCase rangeLoad;
    private final AutoLoadUseCase autoLoad;
    private final PiiVerifyUseCase piiVerify;

    public EtlCli(
            DailyLoadUseCase dailyLoad,
            RangeLoadUseCase rangeLoad,
            AutoLoadUseCase autoLoad,
            PiiVerifyUseCase piiVerify) {
        this.dailyLoad = dailyLoad;
        this.rangeLoad = rangeLoad;
        this.autoLoad = autoLoad;
        this.piiVerify = piiVerify;
    }

    public int execute(LoadCommand command) {
        try {
            if (command.verifyPii()) {
                return reportPii(piiVerify.verify());
            }
            if (command.auto()) {
                return report(autoLoad.loadPending());
            }
            if (command.range()) {
                return report(rangeLoad.loadRange(command.from(), command.to()));
            }
            logLoaded(dailyLoad.load(command.from(), command.rebuild()));
            return EXIT_OK;
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.ERROR, "적재 실패 - " + command, e);
            return EXIT_LOAD_FAILED;
        }
    }

    private static int reportPii(PiiVerificationResult result) {
        result.violations().forEach(violation -> LOG.log(System.Logger.Level.ERROR, "PII 검증 위반 - {0}", violation));
        LOG.log(
                System.Logger.Level.INFO,
                "PII 검증 {0} - 문자열 컬럼 {1}개 검사, 위반 {2}건",
                result.passed() ? "통과" : "실패",
                result.scannedColumns(),
                result.violations().size());
        return result.passed() ? EXIT_OK : EXIT_LOAD_FAILED;
    }

    private static int report(RangeLoadResult result) {
        result.loaded().forEach(EtlCli::logLoaded);
        result.failed()
                .forEach(day -> LOG.log(
                        System.Logger.Level.ERROR, "적재 실패 - baseDate={0}, reason={1}", day.baseDate(), day.reason()));
        LOG.log(
                System.Logger.Level.INFO,
                "기간 적재 종료 - 성공 {0}일, 이미 완료되어 건너뜀 {1}일, 실패 {2}일",
                result.loaded().size(),
                result.skipped().size(),
                result.failed().size());
        return result.hasFailures() ? EXIT_LOAD_FAILED : EXIT_OK;
    }

    private static void logLoaded(LoadResult result) {
        LOG.log(
                System.Logger.Level.INFO,
                "적재 완료 - baseDate={0}, balances={1}, events={2}, total={3}, asOf={4}, loadBatchId={5}",
                result.baseDate(),
                result.balanceRowCount(),
                result.eventRowCount(),
                result.martBalanceTotal(),
                result.asOfDate(),
                result.loadBatchId());
    }
}
