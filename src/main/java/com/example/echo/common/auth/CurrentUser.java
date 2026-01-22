package com.example.echo.common.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 현재 인증된 사용자의 ID를 주입하는 어노테이션(스티커)
 * - 추후 Spring Security 연동 시 구현체 추가 필요
 */
@Target(ElementType.PARAMETER) // @Target - 어디에 붙일 수 있나? = 파라매터
@Retention(RetentionPolicy.RUNTIME) // @Retention - 언제까지 살아있나? = 프로그램 실행 중에도 확인 가능(런타임)
public @interface CurrentUser {
}
