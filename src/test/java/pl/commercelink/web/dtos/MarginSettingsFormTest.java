package pl.commercelink.web.dtos;

import org.junit.jupiter.api.Test;
import pl.commercelink.stores.MarginConfiguration;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarginSettingsFormTest {

    private static MarginSettingsForm form(String defaultPercent, String... categoryAndPercent) {
        MarginSettingsForm form = new MarginSettingsForm();
        form.setDefaultPercent(defaultPercent);
        List<MarginSettingsForm.CategoryRow> rows = new ArrayList<>();
        for (int i = 0; i < categoryAndPercent.length; i += 2) {
            rows.add(new MarginSettingsForm.CategoryRow(categoryAndPercent[i], categoryAndPercent[i + 1]));
        }
        form.setCategories(rows);
        return form;
    }

    @Test
    void aValidFormSavesTheDefaultAndTheFilledRowsSkippingBlankOnes() {
        // given
        MarginSettingsForm form = form("12,5", " CPU ", "5", "", "", "Akcesoria", "25 %");

        // when
        MarginConfiguration configuration = form.toConfiguration();

        // then
        assertThat(form.validate()).isEmpty();
        assertThat(configuration.getDefaultPercent()).isEqualTo(12.5);
        assertThat(configuration.getCategories()).extracting(MarginConfiguration.CategoryMargin::getCategory,
                        MarginConfiguration.CategoryMargin::getPercent)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("CPU", 5.0), org.assertj.core.groups.Tuple.tuple("Akcesoria", 25.0));
    }

    @Test
    void anEmptyFormClearsEverything() {
        // given
        MarginSettingsForm form = form("", "", "");

        // then
        assertThat(form.validate()).isEmpty();
        assertThat(form.toConfiguration().getDefaultPercent()).isNull();
        assertThat(form.toConfiguration().getCategories()).isEmpty();
    }

    @Test
    void wrongPercentsHalfFilledRowsAndRepeatedCategoriesGetAMessageAtTheirField() {
        // given
        MarginSettingsForm form = form("100", "CPU", "abc", "", "10", "cpu", "5", "Storage", "");

        // then
        assertThat(form.validate()).containsExactly(
                java.util.Map.entry("defaultPercent", "store.margins.percent.invalid"),
                java.util.Map.entry("category-0-percent", "store.margins.percent.invalid"),
                java.util.Map.entry("category-1-category", "store.margins.category.required"),
                java.util.Map.entry("category-2-category", "store.margins.category.duplicate"),
                java.util.Map.entry("category-3-percent", "store.margins.percent.required"));
    }

    @Test
    void theFormShowsTheSavedPercentsWithACommaAndOneBlankRowWhenThereAreNone() {
        // when
        MarginSettingsForm saved = MarginSettingsForm.from(new MarginConfiguration(12.5,
                List.of(new MarginConfiguration.CategoryMargin("CPU", 5.0))));
        MarginSettingsForm empty = MarginSettingsForm.from(null);

        // then
        assertThat(saved.getDefaultPercent()).isEqualTo("12,5");
        assertThat(saved.getCategories()).extracting(MarginSettingsForm.CategoryRow::getCategory, MarginSettingsForm.CategoryRow::getPercent)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("CPU", "5"));
        assertThat(empty.getDefaultPercent()).isNull();
        assertThat(empty.getCategories()).hasSize(1);
    }

    @Test
    void aPercentIsAboveZeroBelowAHundredWithAtMostTwoDecimals() {
        assertThat(MarginSettingsForm.parse("12,5")).isEqualByComparingTo("12.5");
        assertThat(MarginSettingsForm.parse(" 12.50 % ")).isEqualByComparingTo("12.5");
        for (String wrong : new String[]{"0", "-5", "100", "abc", "10,555", "1,2,3", ""}) {
            assertThat(MarginSettingsForm.parse(wrong)).as(wrong).isNull();
        }
    }
}
