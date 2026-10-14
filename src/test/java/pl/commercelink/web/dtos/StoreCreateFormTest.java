package pl.commercelink.web.dtos;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StoreCreateFormTest {

    private static StoreCreateForm form(String name, String apiKey) {
        StoreCreateForm form = StoreCreateForm.withId("abcde12345");
        form.setName(name);
        form.setApiKey(apiKey);
        return form;
    }

    @Test
    void blankNameIsRefused() {
        // when
        Map<String, String> errors = form("   ", null).validate();

        // then
        assertThat(errors).containsExactly(Map.entry("name", "store.create.error.name.required"));
    }

    @Test
    void nameIsStripped() {
        // when / then
        assertThat(form("  Elektro-Max \n", null).trimmedName()).isEqualTo("Elektro-Max");
    }

    @Test
    void emptyApiKeyIsAllowedAndStoredAsNothing() {
        // given
        StoreCreateForm form = form("Sklep", "  ");

        // when
        Map<String, String> errors = form.validate();

        // then
        assertThat(errors).isEmpty();
        assertThat(form.apiKeySuffix()).isNull();
    }

    @Test
    void apiKeyShorterThanTheSuffixIsRefused() {
        // when / then
        assertThat(form("Sklep", "ab12c").validate()).containsExactly(Map.entry("apiKey", "store.create.error.apiKey.short"));
    }

    @Test
    void apiKeyThatCannotTravelInAHeaderIsRefused() {
        // when / then — a space inside or a Polish letter would be saved and never match the x-api-key header
        assertThat(form("Sklep", "ab cd ef").validate()).containsExactly(Map.entry("apiKey", "store.create.error.apiKey.chars"));
        assertThat(form("Sklep", "kluczżółć99").validate()).containsExactly(Map.entry("apiKey", "store.create.error.apiKey.chars"));
    }

    @Test
    void apiKeyOfExactlySixCharactersIsKept() {
        // when / then
        assertThat(form("Sklep", "ab12cd").apiKeySuffix()).isEqualTo("ab12cd");
    }

    @Test
    void apiKeySuffixStripsAndKeepsTheLastSix() {
        // given — a whole API Gateway key pasted with stray whitespace
        StoreCreateForm form = form("Sklep", "  Q2xhdWRlQ29kZUFwaUdhdGV3YXlLZXk0MHh5ejEyMw \n");

        // when
        Map<String, String> errors = form.validate();

        // then
        assertThat(errors).isEmpty();
        assertThat(form.apiKeySuffix()).isEqualTo("ejEyMw");
    }

    @Test
    void errorsKeepTheFieldOrder() {
        // when / then
        assertThat(form("", "abc").validate().keySet()).containsExactly("name", "apiKey");
    }

    @Test
    void idShapeIsChecked() {
        // given
        StoreCreateForm missing = StoreCreateForm.withId(null);
        StoreCreateForm forged = StoreCreateForm.withId("../other");

        // when
        StoreCreateForm valid = StoreCreateForm.withId("abcde12345");

        // then
        assertThat(valid.hasWellFormedId()).isTrue();
        assertThat(missing.hasWellFormedId()).isFalse();
        assertThat(forged.hasWellFormedId()).isFalse();
    }
}
