package com.shinhan.corebank.paymentstop.adapter.out.storage;

import com.shinhan.corebank.paymentstop.application.port.out.DocumentStoragePort;
import com.shinhan.corebank.paymentstop.config.DocumentProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * 로컬 파일 시스템 저장소. 시연 VM 과 로컬에서 쓴다. S3 로 옮기면(D36, PH-15) 이 포트 뒤에 구현을 바꾼다.
 *
 * <p>암호화 형식은 12바이트 IV + AES-256-GCM 암호문(태그 128비트 포함)이다. IV 는 파일마다 새로 뽑는다.
 */
@Component
public class LocalDocumentStorage implements DocumentStoragePort {

    private static final Pattern KEY = Pattern.compile("\\d+/\\d+");
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final Path root;
    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public LocalDocumentStorage(DocumentProperties properties) {
        this.root = Path.of(properties.root()).toAbsolutePath().normalize();
        this.key = parseKey(properties.encryptionKey());
    }

    @Override
    public void write(String storageKey, byte[] content, boolean encrypt) {
        Path target = resolve(storageKey);
        byte[] stored = encrypt ? encrypt(content) : content;
        try {
            Files.createDirectories(target.getParent());
            Path temp = Files.createTempFile(target.getParent(), "upload-", ".tmp");
            Files.write(temp, stored);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("첨부 파일을 저장하지 못했습니다. key=" + storageKey, e);
        }
    }

    @Override
    public byte[] read(String storageKey, boolean encrypted) {
        try {
            byte[] stored = Files.readAllBytes(resolve(storageKey));
            return encrypted ? decrypt(stored) : stored;
        } catch (IOException e) {
            throw new UncheckedIOException("첨부 파일을 읽지 못했습니다. key=" + storageKey, e);
        }
    }

    // 키는 서버가 ID 로만 만들지만, 형식을 한 번 더 묶어 루트 밖으로 나가지 못하게 한다
    private Path resolve(String storageKey) {
        if (storageKey == null || !KEY.matcher(storageKey).matches()) {
            throw new IllegalArgumentException("저장소 키 형식이 아닙니다.");
        }
        Path path = root.resolve(storageKey).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("저장소 루트 밖 경로입니다.");
        }
        return path;
    }

    private byte[] encrypt(byte[] plain) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, requireKey(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] sealed = cipher.doFinal(plain);
            return ByteBuffer.allocate(iv.length + sealed.length)
                    .put(iv)
                    .put(sealed)
                    .array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("첨부 파일을 암호화하지 못했습니다.", e);
        }
    }

    private byte[] decrypt(byte[] stored) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, requireKey(), new GCMParameterSpec(TAG_BITS, stored, 0, IV_BYTES));
            return cipher.doFinal(stored, IV_BYTES, stored.length - IV_BYTES);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("첨부 파일을 복호화하지 못했습니다.", e);
        }
    }

    private SecretKey requireKey() {
        if (key == null) {
            throw new IllegalStateException("신분증 사본 암호화 키(PAYMENT_STOP_DOCUMENT_KEY)가 설정되지 않았습니다.");
        }
        return key;
    }

    private static SecretKey parseKey(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        byte[] bytes = Base64.getDecoder().decode(encoded.strip());
        if (bytes.length != 32) {
            throw new IllegalStateException("PAYMENT_STOP_DOCUMENT_KEY 는 32바이트를 base64 로 넣어야 합니다.");
        }
        return new SecretKeySpec(bytes, "AES");
    }
}
