package com.example.echo.memory.support;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * 벡터 유사도 계산 (전용 벡터DB 없이 애플리케이션에서 전수 계산)
 *
 * 사용자당 기억이 수백 건 수준이라 전수 계산이 1ms 안쪽이다.
 * 규모가 커져 벡터DB로 옮길 때 교체 지점은 topK 하나다.
 */
public final class VectorMath {

    private VectorMath() {
    }

    /**
     * 코사인 유사도 (-1 ~ 1). 영벡터가 끼면 0
     */
    public static double cosine(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("벡터 차원 불일치: " + a.length + " vs " + b.length);
        }
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * 질의와 유사도가 threshold 이상인 것 중 상위 k개 (유사도 내림차순)
     *
     * @param vectorOf 항목에서 벡터를 꺼내는 함수. null을 반환하는 항목(임베딩 없음)은 건너뛴다
     */
    public static <T> List<Scored<T>> topK(float[] query, List<T> items, Function<T, float[]> vectorOf,
                                           int k, double threshold) {
        List<Scored<T>> scored = new ArrayList<>();
        for (T item : items) {
            float[] vector = vectorOf.apply(item);
            if (vector == null) {
                continue;
            }
            double score = cosine(query, vector);
            if (score >= threshold) {
                scored.add(new Scored<>(item, score));
            }
        }
        scored.sort(Comparator.comparingDouble((Scored<T> s) -> s.score()).reversed());
        return scored.size() > k ? new ArrayList<>(scored.subList(0, k)) : scored;
    }

    public record Scored<T>(T item, double score) {
    }
}
