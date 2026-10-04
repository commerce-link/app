package pl.commercelink.stores;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarginConfigurationTest {

    private static final MarginConfiguration MARGINS = new MarginConfiguration(10.0, List.of(
            new MarginConfiguration.CategoryMargin("CPU", 5.0),
            new MarginConfiguration.CategoryMargin("Akcesoria", 25.0)));

    @Test
    void anItemTakesItsCategorysThresholdIgnoringCaseAndSpaces() {
        assertThat(MARGINS.thresholdFor(" cpu ")).isEqualTo(new MarginConfiguration.Threshold(5.0, "CPU"));
        assertThat(MARGINS.thresholdFor("Akcesoria")).isEqualTo(new MarginConfiguration.Threshold(25.0, "Akcesoria"));
    }

    @Test
    void anItemOfAnotherCategoryOrWithoutOneTakesTheDefault() {
        assertThat(MARGINS.thresholdFor("Storage")).isEqualTo(new MarginConfiguration.Threshold(10.0, null));
        assertThat(MARGINS.thresholdFor(null)).isEqualTo(new MarginConfiguration.Threshold(10.0, null));
        assertThat(MARGINS.thresholdFor("  ")).isEqualTo(new MarginConfiguration.Threshold(10.0, null));
    }

    @Test
    void withoutADefaultOnlyTheListedCategoriesHaveAThreshold() {
        // given
        MarginConfiguration margins = new MarginConfiguration(null, List.of(new MarginConfiguration.CategoryMargin("CPU", 5.0)));

        // then
        assertThat(margins.thresholdFor("CPU")).isEqualTo(new MarginConfiguration.Threshold(5.0, "CPU"));
        assertThat(margins.thresholdFor("Storage")).isNull();
        assertThat(new MarginConfiguration().thresholdFor("CPU")).isNull();
    }
}
