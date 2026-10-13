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
        // expect
        assertThat(form("  Elektro-Max \n", null).trimmedName()).isEqualTo("Elektro-Max");
    }

    @Test
    void emptyApiKeyIsAllowedAndStoredAsNothing() {
        // given
        StoreCreateForm form = form("Sklep", "  ");

        // expect
        assertThat(form.validate()).isEmpty();
        assertThat(form.apiKeySuffix()).isNull();
    }

    @Test
    void apiKeyShorterThanTheSuffixIsRefused() {
        // expect
        assertThat(form("Sklep", "ab12c").validate()).containsExactly(Map.entry("apiKey", "store.create.error.apiKey.short"));
    }

    @Test
    void apiKeyOfExactlySixCharactersIsKept() {
        // expect
        assertThat(form("Sklep", "ab12cd").apiKeySuffix()).isEqualTo("ab12cd");
    }

    @Test
    void apiKeySuffixStripsAndKeepsTheLastSix() {
        // given — a whole API Gateway key pasted with stray whitespace
        StoreCreateForm form = form("Sklep", "  Q2xhdWRlQ29kZUFwaUdhdGV3YXlLZXk0MHh5ejEyMw \n");

        // expect
        assertThat(form.validate()).isEmpty();
        assertThat(form.apiKeySuffix()).isEqualTo("ejEyMw");
    }

    @Test
    void errorsKeepTheFieldOrder() {
        // expect
        assertThat(form("", "abc").validate().keySet()).containsExactly("name", "apiKey");
    }

    @Test
    void idShapeIsChecked() {
        // given
        StoreCreateForm missing = StoreCreateForm.withId(null);
        StoreCreateForm forged = StoreCreateForm.withId("../other");

        // expect
        assertThat(StoreCreateForm.withId("abcde12345").hasWellFormedId()).isTrue();
        assertThat(missing.hasWellFormedId()).isFalse();
        assertThat(forged.hasWellFormedId()).isFalse();
    }
}
