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

        // expect
        assertThat(form.validate()).containsExactly(Map.entry("newStoreName", "store.copy.error.name.required"));
    }

    @Test
    void nameIsStrippedAndIdShapeChecked() {
        // given
        StoreCopyForm form = StoreCopyForm.of("  Kopia  ", "ABC");

        // expect
        assertThat(form.trimmedName()).isEqualTo("Kopia");
        assertThat(form.hasWellFormedId()).isFalse();
    }
}
