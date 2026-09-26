/*
 * OpenAI Embeddings API 요청 DTO
 *
 * API 문서: https://platform.openai.com/docs/api-reference/embeddings/create
 */
package com.example.echo.memory.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class EmbeddingRequest {

    /** 임베딩할 문장들 (배열로 보내면 한 번의 호출로 여러 개를 받는다) */
    private List<String> input;

    /** 사용할 모델 (예: text-embedding-3-small) */
    private String model;

    /** 출력 차원 수 (text-embedding-3 계열만 지원) */
    private Integer dimensions;
}
