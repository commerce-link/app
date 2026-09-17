package pl.commercelink.stores;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BrandColorTest {

    @Test
    void acceptsSixDigitHexAndStoresItInLowerCase() {
        // when / then
        assertThat(BrandColor.normalize("#1B4DB1")).contains("#1b4db1");
    }

    @Test
    void expandsThreeDigitHexAndAddsAMissingHash() {
        // when / then
        assertThat(BrandColor.normalize("f5a")).contains("#ff55aa");
        assertThat(BrandColor.normalize(" #abc ")).contains("#aabbcc");
    }

    @Test
    void rejectsAnythingThatIsNotAHexColor() {
        // when / then
        assertThat(BrandColor.normalize("red")).isEmpty();
        assertThat(BrandColor.normalize("rgb(0,0,0)")).isEmpty();
        assertThat(BrandColor.normalize("#1b4db1;background-image:url(https://example.com/x.png)")).isEmpty();
        assertThat(BrandColor.normalize("#12345")).isEmpty();
        assertThat(BrandColor.normalize("")).isEmpty();
        assertThat(BrandColor.normalize(null)).isEmpty();
    }
}
