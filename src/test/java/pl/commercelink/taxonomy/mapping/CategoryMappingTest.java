package pl.commercelink.taxonomy.mapping;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CategoryMappingTest {

    @Test
    void staysLearningBelowMinSamples() {
        // given
        CategoryMapping mapping = CategoryMapping.learning("Acme", "karty graficzne", "Karty Graficzne");

        // when
        for (int i = 0; i < 4; i++) {
            mapping.recordSample("301", "GPU", 5, 0.9);
        }

        // then
        assertThat(mapping.isActive()).isFalse();
        assertThat(mapping.getState()).isEqualTo(CategoryMapping.LEARNING);
        assertThat(mapping.getActiveCategoryId()).isNull();
        assertThat(mapping.getTotal()).isEqualTo(4);
    }

    @Test
    void promotesWinnerAtMinSamplesAndFullShare() {
        // given
        CategoryMapping mapping = CategoryMapping.learning("Acme", "karty graficzne", "Karty Graficzne");

        // when
        for (int i = 0; i < 5; i++) {
            mapping.recordSample("301", "GPU", 5, 0.9);
        }

        // then
        assertThat(mapping.isActive()).isTrue();
        assertThat(mapping.getActiveCategoryId()).isEqualTo("301");
        assertThat(mapping.getActiveCategoryName()).isEqualTo("GPU");
    }

    @Test
    void promotesAtExactShareBoundary() {
        // given (9 z 10 = dokładnie 0.9)
        CategoryMapping mapping = CategoryMapping.learning("Acme", "karty graficzne", "Karty Graficzne");
        mapping.recordSample("999", "Inna", 5, 0.9);

        // when
        for (int i = 0; i < 9; i++) {
            mapping.recordSample("301", "GPU", 5, 0.9);
        }

        // then
        assertThat(mapping.isActive()).isTrue();
        assertThat(mapping.getActiveCategoryId()).isEqualTo("301");
    }

    @Test
    void staysLearningWhenShareBelowThreshold() {
        // given (5 z 6 = 0.83 < 0.9)
        CategoryMapping mapping = CategoryMapping.learning("Acme", "akcesoria", "Akcesoria");
        mapping.recordSample("999", "Inna", 5, 0.9);

        // when
        for (int i = 0; i < 5; i++) {
            mapping.recordSample("301", "GPU", 5, 0.9);
        }

        // then
        assertThat(mapping.isActive()).isFalse();
    }

    @Test
    void demotesActiveMappingWhenShareDrops() {
        // given
        CategoryMapping mapping = CategoryMapping.learning("Acme", "karty graficzne", "Karty Graficzne");
        for (int i = 0; i < 5; i++) {
            mapping.recordSample("301", "GPU", 5, 0.9);
        }
        assertThat(mapping.isActive()).isTrue();

        // when (5 z 6 = 0.83 < 0.9)
        mapping.recordSample("999", "Inna", 5, 0.9);

        // then
        assertThat(mapping.isActive()).isFalse();
        assertThat(mapping.getState()).isEqualTo(CategoryMapping.LEARNING);
        assertThat(mapping.getActiveCategoryId()).isNull();
        assertThat(mapping.getActiveCategoryName()).isNull();
    }

    @Test
    void rejectsNewCandidateBeyondCap() {
        // given
        CategoryMapping mapping = CategoryMapping.learning("Acme", "promocje", "Promocje");
        for (int i = 0; i < 20; i++) {
            mapping.recordSample("cat-" + i, "Kategoria " + i, 5, 0.9);
        }

        // when
        boolean accepted = mapping.recordSample("cat-20", "Kategoria 20", 5, 0.9);

        // then
        assertThat(accepted).isFalse();
        assertThat(mapping.getTotal()).isEqualTo(20);
        assertThat(mapping.getCounts()).hasSize(20);
    }

    @Test
    void acceptsSampleOfKnownCandidateAtCap() {
        // given
        CategoryMapping mapping = CategoryMapping.learning("Acme", "promocje", "Promocje");
        for (int i = 0; i < 20; i++) {
            mapping.recordSample("cat-" + i, "Kategoria " + i, 5, 0.9);
        }

        // when
        boolean accepted = mapping.recordSample("cat-0", "Kategoria 0", 5, 0.9);

        // then
        assertThat(accepted).isTrue();
        assertThat(mapping.getTotal()).isEqualTo(21);
    }

    @Test
    void recordSampleUpdatesLastMatchAt() {
        // given
        CategoryMapping mapping = CategoryMapping.learning("Acme", "karty graficzne", "Karty Graficzne");

        // when
        mapping.recordSample("301", "GPU", 5, 0.9);

        // then
        assertThat(mapping.getLastMatchAt()).isNotNull();
    }
}
