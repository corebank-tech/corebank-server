package com.shinhan.corebank.fds.application.port.out;

import java.util.UUID;

/** HELD 이후에만 호출하는 Python 행동 점수 추론 계약. */
public interface BehaviorScoreInferencePort {

    Result infer(Request request);

    record Features(
            int backCount,
            int amountRevisionCount,
            Integer confirmActiveSeconds,
            Integer recipientConfirmSeconds,
            int totalFlowSeconds) {}

    record Request(long transferHoldId, UUID flowId, String featureVersion, Features features) {}

    record Score(double value, String modelVersion, String featureVersion) {}

    enum Failure {
        INPUT_INVALID,
        SERVICE_UNAVAILABLE,
        MODEL_UNAVAILABLE,
        TIMEOUT,
        INFERENCE_FAILED
    }

    record Result(Score score, Failure failure) {
        public static Result available(Score score) {
            return new Result(score, null);
        }

        public static Result unavailable(Failure failure) {
            return new Result(null, failure);
        }
    }
}
