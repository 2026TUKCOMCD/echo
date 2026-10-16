package com.example.echo.common;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.TimeZone;

/**
 * 운영 서버처럼 JVM 기본 시간대가 한국이 아닌 상황을 재현하는 테스트 도우미 (운영 JVM은 UTC).
 *
 * 테스트를 언제 돌리든 JVM 기준 날짜가 한국 날짜보다 하루 이르도록 오프셋을 고른다 -
 * 현지 시각이 "한국 시각 - (시 + 1)시간", 즉 항상 전날 23시대가 된다.
 * 개발 PC는 한국 시간이라 LocalDate.now()에 기대는 코드도 평소에는 테스트를 통과하므로, 이 상황을 직접 만들어야 잡힌다.
 *
 * <pre>
 * try (NonKstJvmTimeZone tz = NonKstJvmTimeZone.apply()) {
 *     ... tz.kstToday() ...
 * }
 * </pre>
 */
public final class NonKstJvmTimeZone implements AutoCloseable {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final TimeZone original = TimeZone.getDefault();
    private final LocalDate kstToday;

    private NonKstJvmTimeZone() {
        ZonedDateTime kstNow = ZonedDateTime.now(KST);
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneOffset.ofHours(8 - kstNow.getHour())));
        kstToday = kstNow.toLocalDate();
    }

    public static NonKstJvmTimeZone apply() {
        return new NonKstJvmTimeZone();
    }

    /** 한국 기준 오늘 (JVM 기준 오늘보다 하루 늦다) */
    public LocalDate kstToday() {
        return kstToday;
    }

    @Override
    public void close() {
        TimeZone.setDefault(original);
    }
}
