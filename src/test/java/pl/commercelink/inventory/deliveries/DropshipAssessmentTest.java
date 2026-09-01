package pl.commercelink.inventory.deliveries;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DropshipAssessmentTest {

    @Test
    void anAssessmentWithProvidersCarriesNoRejection() {
        DropshipAssessment assessment = DropshipAssessment.of(List.of("Acme", "Elko"));

        assertThat(assessment.hasProviders()).isTrue();
        assertThat(assessment.providers()).containsExactly("Acme", "Elko");
        assertThat(assessment.rejection()).isNull();
    }

    @Test
    void aRejectedAssessmentCarriesNoProviders() {
        DropshipAssessment assessment = DropshipAssessment.rejected(DropshipRejection.NO_SHIPPING_DETAILS);

        assertThat(assessment.hasProviders()).isFalse();
        assertThat(assessment.providers()).isEmpty();
        assertThat(assessment.rejection()).isEqualTo(DropshipRejection.NO_SHIPPING_DETAILS);
    }

    @Test
    void supportsAnswersOnlyForListedProviders() {
        DropshipAssessment assessment = DropshipAssessment.of(List.of("Acme"));

        assertThat(assessment.supports("Acme")).isTrue();
        assertThat(assessment.supports("Elko")).isFalse();
    }

    @Test
    void aRejectedAssessmentSupportsNobody() {
        DropshipAssessment assessment = DropshipAssessment.rejected(DropshipRejection.NOTHING_ALLOCATED);

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
