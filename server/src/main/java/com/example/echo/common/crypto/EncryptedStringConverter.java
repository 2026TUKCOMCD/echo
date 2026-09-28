package com.example.echo.common.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 개인정보 컬럼용 AES-256-GCM 암호화 컨버터.
 *
 * 자가 마이그레이션 방식: DB 값이 {@link #PREFIX}로 시작하면 암호문으로 간주해 복호화하고,
 * 아니면 기존에 저장된 평문 레거시 데이터로 간주해 그대로 반환한다. 쓰기는 항상 암호화하므로,
 * 값이 한 번이라도 갱신되면 자동으로 암호화된 형태로 전환된다 (별도 일괄 마이그레이션 불필요).
 *
 * 키는 ENCRYPTION_KEY 환경변수(Base64 인코딩된 32바이트 AES-256 키)에서 읽는다.
 * Hibernate가 JPA 메타데이터 부트스트랩 시점에 무조건 이 컨버터를 인스턴스화하므로,
 * 키 검증은 실제 암호화/복호화가 필요한 시점까지 지연시킨다 (Spring 빈 주입 미사용, 무인자 생성자 유지).
 */
@Slf4j
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private static final String PREFIX = "enc:v1:";
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int KEY_LENGTH_BYTES = 32;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            SecretKeySpec key = loadKey();

            byte[] iv = new byte[GCM_IV_LENGTH];
            SECURE_RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] cipherText = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));

            byte[] payload = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(cipherText, 0, payload, iv.length, cipherText.length);

            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("민감정보 컬럼 암호화 실패", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        if (!dbData.startsWith(PREFIX)) {
            // 아직 암호화되지 않은 레거시 평문 데이터 - 다음 저장 시 자동으로 암호화됨
            log.debug("암호화되지 않은 레거시 평문 값을 읽었습니다 (다음 저장 시 자동 암호화됨)");
            return dbData;
        }
        try {
            SecretKeySpec key = loadKey();

            byte[] payload = Base64.getDecoder().decode(dbData.substring(PREFIX.length()));
            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(payload, 0, iv, 0, GCM_IV_LENGTH);
            byte[] cipherText = new byte[payload.length - GCM_IV_LENGTH];
            System.arraycopy(payload, GCM_IV_LENGTH, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] plainText = cipher.doFinal(cipherText);

            return new String(plainText, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("민감정보 컬럼 복호화 실패", e);
        }
    }

    private SecretKeySpec loadKey() {
        String encoded = System.getenv("ENCRYPTION_KEY");
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalStateException(
                    "ENCRYPTION_KEY 환경변수가 설정되지 않았습니다. 민감정보 컬럼을 암호화/복호화할 수 없습니다.");
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("ENCRYPTION_KEY가 올바른 Base64 형식이 아닙니다.", e);
        }
        if (keyBytes.length != KEY_LENGTH_BYTES) {
            throw new IllegalStateException(
                    "ENCRYPTION_KEY는 Base64로 인코딩된 32바이트(AES-256) 키여야 합니다. 현재 길이: " + keyBytes.length + "바이트");
        }
        return new SecretKeySpec(keyBytes, "AES");
    }
}
