package com.example.echo.memory.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class VectorMathTest {

    @Nested
    @DisplayName("cosine")
    class Cosine {

        @Test
        @DisplayName("같은 방향이면 1 (크기는 무관)")
        void sameDirection() {
            assertThat(VectorMath.cosine(new float[]{1, 2, 3}, new float[]{2, 4, 6})).isCloseTo(1.0, within(1e-6));
        }

        @Test
        @DisplayName("직교하면 0")
        void orthogonal() {
            assertThat(VectorMath.cosine(new float[]{1, 0}, new float[]{0, 1})).isCloseTo(0.0, within(1e-6));
        }

        @Test
        @DisplayName("반대 방향이면 -1")
        void opposite() {
            assertThat(VectorMath.cosine(new float[]{1, 1}, new float[]{-1, -1})).isCloseTo(-1.0, within(1e-6));
        }

        @Test
        @DisplayName("영벡터가 끼면 0 (NaN이 아님)")
        void zeroVector() {
            assertThat(VectorMath.cosine(new float[]{0, 0}, new float[]{1, 1})).isEqualTo(0.0);
        }

        @Test
        @DisplayName("차원이 다르면 거부한다")
        void dimensionMismatch() {
            assertThatThrownBy(() -> VectorMath.cosine(new float[]{1, 0}, new float[]{1, 0, 0}))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("topK")
    class TopK {

        record Item(String name, float[] vector) {
        }

        private final Function<Item, float[]> vectorOf = Item::vector;

        // 질의 (1, 0) 기준 유사도: a=1.0, b≈0.894, c≈0.707, d=0.0
        private final Item a = new Item("a", new float[]{1, 0});
        private final Item b = new Item("b", new float[]{2, 1});
        private final Item c = new Item("c", new float[]{1, 1});
        private final Item d = new Item("d", new float[]{0, 1});
        private final float[] query = {1, 0};

        @Test
        @DisplayName("유사도 내림차순으로 정렬한다")
        void sortedByScore() {
            List<VectorMath.Scored<Item>> result = VectorMath.topK(query, List.of(d, c, a, b), vectorOf, 10, -1.0);

            assertThat(result).extracting(s -> s.item().name()).containsExactly("a", "b", "c", "d");
        }

        @Test
        @DisplayName("상위 k개만 반환한다")
        void limitsToK() {
            List<VectorMath.Scored<Item>> result = VectorMath.topK(query, List.of(d, c, a, b), vectorOf, 2, -1.0);

            assertThat(result).extracting(s -> s.item().name()).containsExactly("a", "b");
        }

        @Test
        @DisplayName("임계값 미만은 제외한다 (임계값과 같으면 포함)")
        void filtersByThreshold() {
            List<VectorMath.Scored<Item>> result = VectorMath.topK(query, List.of(d, c, a, b), vectorOf, 10, 0.8);

            assertThat(result).extracting(s -> s.item().name()).containsExactly("a", "b");
            assertThat(VectorMath.topK(query, List.of(a), vectorOf, 10, 1.0)).hasSize(1);
        }

        @Test
        @DisplayName("벡터가 없는 항목(임베딩 null)은 건너뛴다")
        void skipsMissingVector() {
            Item noVector = new Item("none", null);

            List<VectorMath.Scored<Item>> result = VectorMath.topK(query, List.of(noVector, a), vectorOf, 10, -1.0);

            assertThat(result).extracting(s -> s.item().name()).containsExactly("a");
        }

        @Test
        @DisplayName("임계값을 넘는 것이 없으면 빈 리스트")
        void emptyWhenNothingPasses() {
            assertThat(VectorMath.topK(query, List.of(d), vectorOf, 3, 0.5)).isEmpty();
        }
    }
}
