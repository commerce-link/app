package pl.commercelink.orders.rma;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.ShippingDetails;

import static org.assertj.core.api.Assertions.assertThat;

class RmaSearchTest {

    private static RMA rma() {
        RMA rma = new RMA();
        rma.setRmaId("e3b0c442-98fc-4c14-9afb-f4c8996fb924");
        rma.setOrderId("8c1d0291-0000-4000-8000-000000000000");
        rma.setEmail("Jan.Kowalski@Gmail.com");
        rma.setExternalReturnReference("ZW-4K7Q");
        ShippingDetails details = new ShippingDetails();
        details.setName("Łukasz");
        details.setSurname("Kowalski");
        details.setCompanyName("Studio Graficzne Pixel");
        rma.setShippingDetails(details);
        return rma;
    }

    @Test
    void matchesWithoutCaseInNamesEmailAndCompany() {
        // when / then
        assertThat(RmaSearch.matches(rma(), "KOWALSKI")).isTrue();
        assertThat(RmaSearch.matches(rma(), "łukasz kowalski")).isTrue();
        assertThat(RmaSearch.matches(rma(), "jan.kowalski@gmail")).isTrue();
        assertThat(RmaSearch.matches(rma(), "pixel")).isTrue();
    }

    @Test
    void matchesTheShortNumbersShownInTheTableAndTheMarketplaceReference() {
        // when / then
        assertThat(RmaSearch.matches(rma(), "E3B0C442")).isTrue();
        assertThat(RmaSearch.matches(rma(), "8c1d0291")).isTrue();
        assertThat(RmaSearch.matches(rma(), "zw-4k7q")).isTrue();
        assertThat(RmaSearch.matches(rma(), "nowak")).isFalse();
    }

    @Test
    void matchesWithoutPolishDiacriticsEitherWay() {
        // when / then
        assertThat(RmaSearch.matches(rma(), "lukasz")).isTrue();
        assertThat(RmaSearch.matches(rma(), "ŁUKASZ")).isTrue();
        assertThat(RmaSearch.matches(rma(), "graficzne")).isTrue();
        RMA zielinska = rma();
        zielinska.getShippingDetails().setSurname("Zielińska-Ćwiek");
        assertThat(RmaSearch.matches(zielinska, "zielinska-cwiek")).isTrue();
        assertThat(RmaSearch.matches(zielinska, "zielińska")).isTrue();
    }

    @Test
    void aBareRmaMatchesOnlyItsId() {
        // given
        RMA bare = new RMA();
        bare.setRmaId("a1");

        // when / then
        assertThat(RmaSearch.matches(bare, "a1")).isTrue();
        assertThat(RmaSearch.matches(bare, "x")).isFalse();
    }
}
