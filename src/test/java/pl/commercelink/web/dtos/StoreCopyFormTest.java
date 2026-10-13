package pl.commercelink.web.dtos;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StoreCopyFormTest {

    @Test
    void suggestedNameAndIdArePrefilled() {
        // when
        StoreCopyForm form = StoreCopyForm.of("Madman Gaming (kopia)", "abcde12345");

        // then
        assertThat(form.getNewStoreName()).isEqualTo("Madman Gaming (kopia)");
        assertThat(form.getNewStoreId()).isEqualTo("abcde12345");
        assertThat(form.validate()).isEmpty();
    }

    @Test
    void blankNameIsRefused() {
        // given
        StoreCopyForm form = StoreCopyForm.of(" \t", "abcde12345");

        // when
        Map<String, String> errors = form.validate();

        // then
        assertThat(errors).containsExactly(Map.entry("newStoreName", "store.copy.error.name.required"));
    }

    @Test
    void nameIsStrippedAndIdShapeChecked() {
        // given
        StoreCopyForm form = StoreCopyForm.of("  Kopia  ", "ABC");

        // when
        String name = form.trimmedName();
        boolean wellFormed = form.hasWellFormedId();

        // then
        assertThat(name).isEqualTo("Kopia");
        assertThat(wellFormed).isFalse();
    }
}
