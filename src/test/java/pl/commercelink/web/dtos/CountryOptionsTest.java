package pl.commercelink.web.dtos;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class CountryOptionsTest {

    @Test
    void listsPolandFirstAndNamesCountriesInTheUserLanguage() {
        // when
        List<PickerOption> polish = CountryOptions.forPicker("PL", Locale.forLanguageTag("pl"));
        List<PickerOption> english = CountryOptions.forPicker("PL", Locale.ENGLISH);

        // then
        assertThat(polish.get(0)).isEqualTo(new PickerOption("PL", "Polska"));
        assertThat(polish).contains(new PickerOption("DE", "Niemcy"));
        assertThat(english.get(0)).isEqualTo(new PickerOption("PL", "Poland"));
        assertThat(polish).extracting(PickerOption::value).doesNotHaveDuplicates().hasSize(27);
    }

    @Test
    void keepsAStoredCountryThatIsNotOnTheList() {
        // when
        List<PickerOption> options = CountryOptions.forPicker("Polska", Locale.forLanguageTag("pl"));

        // then
        assertThat(options.get(0)).isEqualTo(new PickerOption("Polska", "Polska"));
        assertThat(options).hasSize(28);
    }

    @Test
    void namesAStoredCountryCodeForAnAddressSummary() {
        // when / then
        assertThat(CountryOptions.displayName("DE", Locale.forLanguageTag("pl"))).isEqualTo("Niemcy");
        assertThat(CountryOptions.displayName("Polska", Locale.forLanguageTag("pl"))).isEqualTo("Polska");
        assertThat(CountryOptions.displayName(null, Locale.forLanguageTag("pl"))).isNull();
    }
}
