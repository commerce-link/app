package pl.commercelink.shipping;

import org.junit.jupiter.api.Test;
import pl.commercelink.stores.AuthorizedCarrier;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShippingServiceCarrierMatchingTest {

    private static final AuthorizedCarrier INPOST = new AuthorizedCarrier("1", "inpost", "InPost Paczkomaty");
    private static final AuthorizedCarrier INPOST_COURIER = new AuthorizedCarrier("4", "inpost_courier", "InPost Kurier");
    private static final AuthorizedCarrier DPD = new AuthorizedCarrier("2", "dpd", "DPD Kurier");
    private static final AuthorizedCarrier POCZTA = new AuthorizedCarrier("3", "poczta", "Poczta Polska");

    private final List<AuthorizedCarrier> authorized = List.of(INPOST, INPOST_COURIER, DPD, POCZTA);

    @Test
    void narrowsToTheServicesOfTheCode() {
        // when
        List<AuthorizedCarrier> matching = ShippingService.carriersMatching(authorized, Optional.of("inpost"));

        // then
        assertEquals(List.of(INPOST), matching);
    }

    @Test
    void keepsEveryServiceWithoutACode() {
        // when
        List<AuthorizedCarrier> matching = ShippingService.carriersMatching(authorized, Optional.empty());

        // then
        assertEquals(authorized, matching);
    }

    @Test
    void fallsBackToEveryServiceWhenNoneHasTheCode() {
        // when
        List<AuthorizedCarrier> matching = ShippingService.carriersMatching(authorized, Optional.of("gls"));

        // then
        assertEquals(authorized, matching);
    }
}
