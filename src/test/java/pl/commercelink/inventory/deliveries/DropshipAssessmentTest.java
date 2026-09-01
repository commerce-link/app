package pl.commercelink.inventory.deliveries;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DropshipAssessmentTest {

    @Test
    void anAssessmentWithProvidersCarriesNoRejection() {
        // given
        DropshipAssessment assessment = DropshipAssessment.of(List.of("Acme", "Elko"));

        // when / then
        assertThat(assessment.hasProviders()).isTrue();
        assertThat(assessment.providers()).containsExactly("Acme", "Elko");
        assertThat(assessment.rejection()).isNull();
    }

    @Test
    void aRejectedAssessmentCarriesNoProviders() {
        // given
        DropshipAssessment assessment = DropshipAssessment.rejected(DropshipRejection.NO_SHIPPING_DETAILS);

        // when / then
        assertThat(assessment.hasProviders()).isFalse();
        assertThat(assessment.providers()).isEmpty();
        assertThat(assessment.rejection()).isEqualTo(DropshipRejection.NO_SHIPPING_DETAILS);
    }

    @Test
    void supportsAnswersOnlyForListedProviders() {
        // given
        DropshipAssessment assessment = DropshipAssessment.of(List.of("Acme"));

        // when / then
        assertThat(assessment.supports("Acme")).isTrue();
        assertThat(assessment.supports("Elko")).isFalse();
    }

    @Test
    void supportsAnswersFalseForANullProviderInsteadOfThrowing() {
        // given: List.of(...).contains(null) throws, so the record has to answer for itself
        DropshipAssessment assessment = DropshipAssessment.of(List.of("Acme"));

        // when / then
        assertThat(assessment.supports(null)).isFalse();
        assertThat(DropshipAssessment.rejected(DropshipRejection.NOTHING_ALLOCATED).supports(null)).isFalse();
    }

    @Test
    void aRejectedAssessmentSupportsNobody() {
        // given
        DropshipAssessment assessment = DropshipAssessment.rejected(DropshipRejection.NOTHING_ALLOCATED);

        // when / then
        assertThat(assessment.supports("Acme")).isFalse();
    }

    @Test
    void constructingDirectlyDefendsAgainstMutationOfTheSourceList() {
        // given
        List<String> mutable = new ArrayList<>(List.of("Acme"));

        // when
        DropshipAssessment assessment = new DropshipAssessment(mutable, null);
        mutable.add("Elko");

        // then
        assertThat(assessment.providers()).containsExactly("Acme");
    }
}
