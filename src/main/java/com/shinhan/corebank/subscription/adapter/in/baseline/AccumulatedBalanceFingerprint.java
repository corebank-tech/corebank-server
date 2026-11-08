package com.shinhan.corebank.subscription.adapter.in.baseline;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class AccumulatedBalanceFingerprint {

    private final MessageDigest digest;
    private final ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES * 2);

    AccumulatedBalanceFingerprint() {
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
        }
    }

    void update(long accountId, long balance) {
        buffer.clear();
        buffer.putLong(accountId);
        buffer.putLong(balance);
        digest.update(buffer.array());
    }

    String hex() {
        return HexFormat.of().formatHex(digest.digest());
    }
}
