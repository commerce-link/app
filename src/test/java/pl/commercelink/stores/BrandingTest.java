package pl.commercelink.stores;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BrandingTest {

    @Test
    void customerPagesGetTheStoredColourOnlyWhenItIsAHexColour() {
        // given
        Branding valid = new Branding();
        valid.setPrimaryColor("#1B4DB1");
        Branding injected = new Branding();
        injected.setPrimaryColor("red;background-image:url(https://example.com/x.png)");

        // when / then
        assertThat(valid.getSafePrimaryColor()).isEqualTo("#1b4db1");
        assertThat(injected.getSafePrimaryColor()).isNull();
        assertThat(new Branding().getSafePrimaryColor()).isNull();
    }
}
