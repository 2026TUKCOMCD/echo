/*
 * OpenAI Embeddings API 응답 DTO
 *
 * data[i].index가 요청 input의 순서를 가리킨다
 * API 문서: https://platform.openai.com/docs/api-reference/embeddings/object
 */
package com.example.echo.memory.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class EmbeddingResponse {

    private List<Data> data;

    /** 사용된 모델 */
    private String model;

    @Getter
    @NoArgsConstructor
    public static class Data {

        /** 요청 input 배열에서의 위치 */
        private Integer index;

        private float[] embedding;
    }
}
