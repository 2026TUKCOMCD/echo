package com.example.echo.common.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncryptedLocalDateConverterTest {

    private static final String VALID_KEY = Base64.getEncoder().encodeToString(new byte[32]);

    private static EncryptedLocalDateConverter converterWithKey(String key) {
        return new EncryptedLocalDateConverter() {
            @Override
            protected String encryptionKeyEnvValue() {
                return key;
            }
        };
    }

    @Test
    @DisplayName("암호화 후 복호화하면 원래 LocalDate로 복원된다")
    void encryptThenDecrypt_roundTrips() {
        EncryptedLocalDateConverter converter = converterWithKey(VALID_KEY);

        LocalDate original = LocalDate.of(1950, 1, 1);
        String encrypted = converter.convertToDatabaseColumn(original);

        assertThat(encrypted).startsWith("enc:v1:");
        assertThat(converter.convertToEntityAttribute(encrypted)).isEqualTo(original);
    }

    @Test
    @DisplayName("같은 값도 매번 다른 암호문을 생성한다 (랜덤 IV)")
    void sameValue_producesDifferentCiphertext() {
        EncryptedLocalDateConverter converter = converterWithKey(VALID_KEY);

        LocalDate date = LocalDate.of(1950, 1, 1);
        String first = converter.convertToDatabaseColumn(date);
        String second = converter.convertToDatabaseColumn(date);

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("prefix 없는 레거시 평문(ISO-8601) 값은 키 없이도 그대로 파싱된다")
    void legacyPlaintextDate_parsedWithoutKey() {
        EncryptedLocalDateConverter converter = converterWithKey(null);

        assertThat(converter.convertToEntityAttribute("1950-01-01")).isEqualTo(LocalDate.of(1950, 1, 1));
    }

    @Test
    @DisplayName("null은 그대로 null 반환")
    void nullValue_returnsNull() {
        EncryptedLocalDateConverter converter = converterWithKey(VALID_KEY);

        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    @DisplayName("ENCRYPTION_KEY 미설정 시 암호화 시도하면 즉시 예외 발생")
    void missingKey_throwsOnEncrypt() {
        EncryptedLocalDateConverter converter = converterWithKey(null);

        assertThatThrownBy(() -> converter.convertToDatabaseColumn(LocalDate.of(1950, 1, 1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ENCRYPTION_KEY 환경변수가 설정되지 않았습니다");
    }

    @Test
    @DisplayName("키 길이가 32바이트가 아니면 예외 발생")
    void invalidKeyLength_throws() {
        EncryptedLocalDateConverter converter = converterWithKey(Base64.getEncoder().encodeToString(new byte[16]));

        assertThatThrownBy(() -> converter.convertToDatabaseColumn(LocalDate.of(1950, 1, 1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32바이트");
    }
}
