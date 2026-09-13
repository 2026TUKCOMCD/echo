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
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Base64;

/**
 * 생년월일(birthday) 컬럼용 AES-256-GCM 암호화 컨버터.
 *
 * 같은 스킴(enc:v1: prefix, AES-256-GCM, ENCRYPTION_KEY 환경변수)을 쓰는
 * {@code EncryptedStringConverter}(feat/encrypt-sensitive-columns 브랜치, 아직 develop
 * 미병합)와 크립토 로직이 중복돼 있다. birthday는 LocalDate<->String 경계 변환이 필요해
 * AttributeConverter 타입 자체가 달라 그대로 재사용할 수 없었음 - 두 브랜치가 모두 develop에
 * 병합된 뒤에는 공용 크립토 헬퍼로 통합할 것.
 *
 * DB 컬럼은 DATE에서 TEXT로 변경돼야 하며(마이그레이션 SQL은 PR 설명 참고), 이 컨버터는
 * ISO-8601(yyyy-MM-dd) 문자열로 직렬화한 뒤 암호화한다. 엔티티의 Java 타입은 여전히
 * LocalDate라 나이 계산(Period.between 등) 등 호출부 코드는 변경할 필요가 없다.
 */
@Slf4j
@Converter
public class EncryptedLocalDateConverter implements AttributeConverter<LocalDate, String> {

    private static final String PREFIX = "enc:v1:";
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int KEY_LENGTH_BYTES = 32;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Override
    public String convertToDatabaseColumn(LocalDate attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            SecretKeySpec key = loadKey();

            byte[] iv = new byte[GCM_IV_LENGTH];
            SECURE_RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] cipherText = cipher.doFinal(attribute.toString().getBytes(StandardCharsets.UTF_8));

            byte[] payload = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(cipherText, 0, payload, iv.length, cipherText.length);

            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("생년월일 컬럼 암호화 실패", e);
        }
    }

    @Override
    public LocalDate convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        if (!dbData.startsWith(PREFIX)) {
            // 아직 암호화되지 않은 레거시 평문(ISO-8601) 값 - 다음 저장 시 자동으로 암호화됨
            log.debug("암호화되지 않은 레거시 평문 생년월일 값을 읽었습니다 (다음 저장 시 자동 암호화됨)");
            return parseLegacyDate(dbData);
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

            return LocalDate.parse(new String(plainText, StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("생년월일 컬럼 복호화 실패", e);
        }
    }

    private LocalDate parseLegacyDate(String dbData) {
        try {
            return LocalDate.parse(dbData);
        } catch (DateTimeParseException e) {
            throw new IllegalStateException("생년월일 컬럼 값이 날짜 형식이 아닙니다: " + dbData, e);
        }
    }

    /**
     * ENCRYPTION_KEY 원본 값을 가져온다. 운영에서는 항상 실제 환경변수를 읽지만, 테스트에서는
     * {@code java.lang.System}을 목킹할 수 없어(Mockito가 명시적으로 차단) 이 메서드를
     * 오버라이드한 테스트 서브클래스로 대체한다.
     */
    protected String encryptionKeyEnvValue() {
        return System.getenv("ENCRYPTION_KEY");
    }

    private SecretKeySpec loadKey() {
        String encoded = encryptionKeyEnvValue();
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalStateException(
                    "ENCRYPTION_KEY 환경변수가 설정되지 않았습니다. 생년월일 컬럼을 암호화/복호화할 수 없습니다.");
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
