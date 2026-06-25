package pl.commercelink.taxonomy;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SignalCategoryResolverTest {

    private final SignalCategoryResolver resolver = new SignalCategoryResolver();

    @Test
    void emptySignalsResolveToEmpty() {
        // when / then
        assertThat(resolver.resolve(List.of())).isEmpty();
        assertThat(resolver.resolve(null)).isEmpty();
    }

    @Test
    void vendorCategoryMatchingEnumNameResolvesToThatKey() {
        // when / then
        assertThat(resolver.resolve(List.of("VENDOR_CATEGORY:CPU"))).contains("CPU");
        assertThat(resolver.resolve(List.of("VENDOR_CATEGORY:Laptops"))).contains("Laptops");
    }

    @Test
    void vendorCategoryIsCaseInsensitive() {
        // when / then
        assertThat(resolver.resolve(List.of("VENDOR_CATEGORY:cpu"))).contains("CPU");
    }

    @Test
    void polishVendorSynonymResolves() {
        // when / then
        assertThat(resolver.resolve(List.of("VENDOR_CATEGORY:Procesor"))).contains("CPU");
        assertThat(resolver.resolve(List.of("VENDOR_CATEGORY:Karta graficzna"))).contains("GPU");
    }

    @Test
    void fanIsDisambiguatedFromCoolerAndCase() {
        // the POC cluster: fans confused with cooling/cases. Deterministic resolution keeps them apart.
        // when / then
        assertThat(resolver.resolve(List.of("VENDOR_CATEGORY:Wentylator"))).contains("Fan");
        assertThat(resolver.resolve(List.of("VENDOR_CATEGORY:Chłodzenie"))).contains("Cooler");
        assertThat(resolver.resolve(List.of("VENDOR_CATEGORY:Obudowa"))).contains("Case");
    }

    @Test
    void unknownVendorValueResolvesToEmptyWithNoGuessing() {
        // zero AI: an unmapped value is NOT forced into a category.
        // when / then
        assertThat(resolver.resolve(List.of("VENDOR_CATEGORY:Dog Leash"))).isEmpty();
    }

    @Test
    void otherIsNeverAResolution() {
        // Other is the unresolved sentinel, never a real category to map onto.
        // when / then
        assertThat(resolver.resolve(List.of("VENDOR_CATEGORY:Other"))).isEmpty();
    }

    @Test
    void signalsWithoutVendorCategoryPrefixAreIgnored() {
        // when / then
        assertThat(resolver.resolve(List.of("BRAND:Acme", "TITLE:some gpu card"))).isEmpty();
    }

    @Test
    void firstResolvingSignalWinsDeterministically() {
        // when
        Optional<String> resolved = resolver.resolve(List.of(
                "BRAND:Acme",
                "VENDOR_CATEGORY:GPU",
                "VENDOR_CATEGORY:CPU"));

        // then
        assertThat(resolved).contains("GPU");
    }
}
