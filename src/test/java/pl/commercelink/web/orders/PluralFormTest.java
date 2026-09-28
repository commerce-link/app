package pl.commercelink.web.orders;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PluralFormTest {

    @Test
    void oneIsTheSingularForm() {
        // when
        String form = PluralForm.of(1);

        // then
        assertThat(form).isEqualTo("one");
    }

    @Test
    void twoToFourAndTheirTensTakeTheFewForm() {
        // given
        int[] counts = {2, 3, 4, 22, 23, 24, 32, 102, 104, 1022};

        // when / then
        for (int count : counts) {
            assertThat(PluralForm.of(count)).as("count %d", count).isEqualTo("few");
        }
    }

    @Test
    void fivePlusTheTeensAndZeroTakeTheManyForm() {
        // given
        int[] counts = {0, 5, 6, 9, 10, 11, 12, 13, 14, 15, 20, 21, 25, 101, 111, 112, 114, 1000};

        // when / then
        for (int count : counts) {
            assertThat(PluralForm.of(count)).as("count %d", count).isEqualTo("many");
        }
    }
}
