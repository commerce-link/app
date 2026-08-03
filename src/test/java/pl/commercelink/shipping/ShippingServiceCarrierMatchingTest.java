package pl.commercelink.shipping;

import org.junit.jupiter.api.Test;
import pl.commercelink.stores.AuthorizedCarrier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShippingServiceCarrierMatchingTest {

    private static final AuthorizedCarrier INPOST = new AuthorizedCarrier("1", "inpost", "InPost Paczkomaty");
    private static final AuthorizedCarrier DPD = new AuthorizedCarrier("2", "dpd", "DPD Kurier");
    private static final AuthorizedCarrier POCZTA = new AuthorizedCarrier("3", "poczta", "Poczta Polska");

    private final List<AuthorizedCarrier> authorized = List.of(INPOST, DPD, POCZTA);

    @Test
    void narrowsToTheCarrierChosenByTheBuyer() {
        // when
        List<AuthorizedCarrier> matching = ShippingService.carriersMatching(authorized, "INPOST");

        // then
        assertEquals(List.of(INPOST), matching);
    }

    @Test
    void matchesMultiWordCarrierNames() {
        // when
        List<AuthorizedCarrier> matching = ShippingService.carriersMatching(authorized, "POCZTA_POLSKA");

        // then
        assertEquals(List.of(POCZTA), matching);
    }

    @Test
    void keepsEveryCarrierWhenBuyerChoiceIsUnknown() {
        // when
        List<AuthorizedCarrier> matching = ShippingService.carriersMatching(authorized, null);

        // then
        assertEquals(authorized, matching);
    }

    @Test
    void fallsBackToEveryCarrierWhenChoiceCannotBeMatched() {
        // when
        List<AuthorizedCarrier> matching = ShippingService.carriersMatching(authorized, "MEEST");

        // then
        assertEquals(authorized, matching);
    }
}
