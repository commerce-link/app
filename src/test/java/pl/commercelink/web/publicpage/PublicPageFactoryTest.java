package pl.commercelink.web.publicpage;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.stores.Branding;
import pl.commercelink.stores.Store;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class PublicPageFactoryTest {

    private static final String PATH = "/store/s1/client/offer/o1";
    private static final Locale PL = Locale.forLanguageTag("pl");

    @Test
    void storeWithLogoUsesVersionedLogoUrl() {
        // given
        Store store = store();
        Branding branding = new Branding();
        branding.setLogo("logo.png");
        branding.setLogoVersion(7L);
        store.setBranding(branding);

        // when
        PublicPage page = PublicPageFactory.of(store, PATH, PL);

        // then
        assertThat(page.logoUrl()).isEqualTo("/StoreLogo/s1?v=7");
    }

    @Test
    void storeWithLogoWithoutVersionUsesPlainLogoUrl() {
        // given
        Store store = store();
        Branding branding = new Branding();
        branding.setLogo("logo.png");
        store.setBranding(branding);

        // when
        PublicPage page = PublicPageFactory.of(store, PATH, PL);

        // then
        assertThat(page.logoUrl()).isEqualTo("/StoreLogo/s1");
    }

    @Test
    void storeWithoutBrandingUsesSystemPalette() {
        // given
        Store store = store();
        store.setBranding(null);

        // when
        PublicPage page = PublicPageFactory.of(store, PATH, PL);

        // then
        assertThat(page.logoUrl()).isNull();
        assertThat(page.brand().fill()).isEqualTo("#1b4db1");
    }

    @Test
    void sellerLineComesFromStoreBillingDetails() {
        // given
        Store store = store();
        BillingDetails billing = new BillingDetails();
        billing.setCompanyName("Nova Tech sp. z o.o.");
        billing.setTaxId("5252814410");
        billing.setStreetAndNumber("ul. Prosta 12");
        billing.setPostalCode("00-850");
        billing.setCity("Warszawa");
        billing.setPhone("+48 22 123 45 67");
        store.setBillingDetails(billing);

        // when
        PublicPage page = PublicPageFactory.of(store, PATH, PL);

        // then
        assertThat(page.hasSeller()).isTrue();
        assertThat(page.sellerName()).isEqualTo("Nova Tech sp. z o.o.");
        assertThat(page.sellerTaxId()).isEqualTo("5252814410");
        assertThat(page.sellerAddress()).isEqualTo("ul. Prosta 12, 00-850 Warszawa");
        assertThat(page.contactPhone()).isEqualTo("+48 22 123 45 67");
        assertThat(page.hasContact()).isTrue();
    }

    @Test
    void blankBillingPartsAreDropped() {
        // given
        Store store = store();
        BillingDetails billing = new BillingDetails();
        billing.setCompanyName("  ");
        billing.setCity("Warszawa");
        store.setBillingDetails(billing);

        // when
        PublicPage page = PublicPageFactory.of(store, PATH, PL);

        // then
        assertThat(page.hasSeller()).isFalse();
        assertThat(page.sellerAddress()).isEqualTo("Warszawa");
        assertThat(page.sellerTaxId()).isNull();
    }

    @Test
    void noBillingDetailsMeansNoSellerLine() {
        // given
        Store store = store();
        store.setBillingDetails(null);

        // when
        PublicPage page = PublicPageFactory.of(store, PATH, PL);

        // then
        assertThat(page.hasSeller()).isFalse();
        assertThat(page.contactEmail()).isEqualTo(store.getClientContactEmail());
        assertThat(page.hasContact()).isFalse();
    }

    @Test
    void languageLinkPointsToTheOtherLanguage() {
        // given
        Store store = store();

        // when
        PublicPage polish = PublicPageFactory.of(store, PATH, PL);
        PublicPage english = PublicPageFactory.of(store, PATH, Locale.ENGLISH);
        PublicPage other = PublicPageFactory.of(store, PATH, Locale.GERMAN);

        // then
        assertThat(polish.language()).isEqualTo("pl");
        assertThat(polish.otherLanguage()).isEqualTo("en");
        assertThat(polish.otherLanguageHref()).isEqualTo(PATH + "?lang=en");
        assertThat(english.language()).isEqualTo("en");
        assertThat(english.otherLanguage()).isEqualTo("pl");
        assertThat(english.otherLanguageHref()).isEqualTo(PATH + "?lang=pl");
        assertThat(other.language()).isEqualTo("pl");
    }

    @Test
    void unknownStoreHasNoNameNoFooterAndSystemPalette() {
        // when
        PublicPage page = PublicPageFactory.unknownStore(PATH, PL);

        // then
        assertThat(page.storeName()).isEmpty();
        assertThat(page.storeId()).isNull();
        assertThat(page.logoUrl()).isNull();
        assertThat(page.brand().fill()).isEqualTo("#1b4db1");
        assertThat(page.hasSeller()).isFalse();
        assertThat(page.hasContact()).isFalse();
        assertThat(page.otherLanguageHref()).isEqualTo(PATH + "?lang=en");
    }

    private static Store store() {
        Store store = new Store();
        store.setStoreId("s1");
        store.setName("Nova Tech");
        return store;
    }
}
