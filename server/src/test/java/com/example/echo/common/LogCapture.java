package com.example.echo.common;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

/**
 * 한 클래스의 로그를 가로채는 테스트 도우미 - 로그에 민감정보(API 키·좌표·대화 내용)가 남지 않는지 검사할 때 쓴다.
 *
 * <pre>
 * try (LogCapture logs = LogCapture.of(WeatherClient.class)) {
 *     ...
 *     assertThat(logs.allText()).doesNotContain(API_KEY);
 * }
 * </pre>
 */
public final class LogCapture implements AutoCloseable {

    private final Logger logger;
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private LogCapture(Class<?> type) {
        logger = (Logger) LoggerFactory.getLogger(type);
        appender.start();
        logger.addAppender(appender);
    }

    public static LogCapture of(Class<?> type) {
        return new LogCapture(type);
    }

    /** 로그 메시지와, 함께 남긴 예외(원인·suppressed 포함)의 메시지까지 전부 - 스택 트레이스 첫 줄도 로그에 찍히기 때문 */
    public String allText() {
        StringBuilder text = new StringBuilder();
        for (ILoggingEvent event : appender.list) {
            text.append(event.getFormattedMessage()).append('\n');
            appendThrowable(text, event.getThrowableProxy());
        }
        return text.toString();
    }

    public int count() {
        return appender.list.size();
    }

    private static void appendThrowable(StringBuilder text, IThrowableProxy throwable) {
        if (throwable == null) {
            return;
        }
        text.append(throwable.getClassName()).append(": ").append(throwable.getMessage()).append('\n');
        for (IThrowableProxy suppressed : throwable.getSuppressed()) {
            appendThrowable(text, suppressed);
        }
        appendThrowable(text, throwable.getCause());
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
        appender.stop();
    }
}
