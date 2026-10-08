package pl.commercelink.shipping;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderSource;
import pl.commercelink.orders.OrderSourceType;
import pl.commercelink.rest.client.HttpClientException;
import pl.commercelink.shipping.api.DeliveryPoint;
import pl.commercelink.shipping.api.DeliveryType;
import pl.commercelink.shipping.api.OrderReference;
import pl.commercelink.shipping.api.PackageOption;
import pl.commercelink.shipping.api.ShipmentProposal;
import pl.commercelink.shipping.api.ShippingException;
import pl.commercelink.shipping.api.ShippingProvider;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.Store;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShippingIntegrationChoiceTest {

    @Mock private ShippingProviders providers;
    @Mock private ShippingService shippingService;
    @Mock private ShippingProvider allegro;

    private ShippingIntegrationChoice choice;
    private Store store;

    @BeforeEach
    void setUp() {
        store = new Store();
        store.setStoreId("store-1");
        store.setConfigurationValue(IntegrationType.SHIPPING_PROVIDER, "furgonetka");
        store.addAdditionalShippingIntegration("allegro");
        when(shippingService.isAvailable(store)).thenReturn(true);
        when(providers.forName(store, "allegro")).thenReturn(Optional.of(allegro));
        choice = new ShippingIntegrationChoice(providers, shippingService, ShippingIntegrationNamesFixture.names());
    }

    static Order allegroOrder() {
        Order order = new Order("store-1");
        order.setExternalOrderId("29a9b8c0-a87a-11f1-8456-8d3ada2e8e1c");
        order.setSource(new OrderSource("Allegro", OrderSourceType.Marketplace));
        return order;
    }

    static Order shopOrder() {
        Order order = new Order("store-1");
        order.setSource(new OrderSource("Sklep", OrderSourceType.Other));
        return order;
    }

    static ShipmentProposal proposal() {
        return ShipmentProposal.available("Allegro One Box, One Kurier", "ALLEGRO", new DeliveryPoint("ALBOX-WAW-0231"),
                DeliveryType.LOCKER, List.of(new PackageOption("PACKAGE", new BigDecimal("64"), new BigDecimal("38"),
                        new BigDecimal("41"), new BigDecimal("25"))), new BigDecimal("5000"), new BigDecimal("5000"));
    }

    @Test
    void allegroOrderSuggestsAllegroWhenTheProposalIsAvailable() {
        // given
        when(allegro.proposeShipment(any())).thenReturn(proposal());

        // when
        List<ShippingIntegrationOption> options = choice.forOrder(store, allegroOrder());

        // then
        assertThat(options).extracting(ShippingIntegrationOption::name).containsExactly("furgonetka", "allegro");
        assertThat(options).allMatch(ShippingIntegrationOption::available);
        assertThat(choice.suggested(options)).map(ShippingIntegrationOption::name).contains("allegro");
        assertThat(options.get(1).proposal().methodName()).isEqualTo("Allegro One Box, One Kurier");
    }

    @Test
    void theProposalIsAskedWithTheCheckoutFormOfTheOrder() {
        // given
        when(allegro.proposeShipment(any())).thenReturn(proposal());
        Order order = allegroOrder();

        // when
        choice.forOrder(store, order);

        // then
        verify(allegro).proposeShipment(new OrderReference("Allegro", order.getExternalOrderId(), order.getShortenedOrderId()));
    }

    @Test
    void orderOutsideAllegroGreysAllegroWithoutAskingIt() {
        // when
        List<ShippingIntegrationOption> options = choice.forOrder(store, shopOrder());

        // then
        ShippingIntegrationOption allegroOption = options.get(1);
        assertThat(allegroOption.available()).isFalse();
        assertThat(allegroOption.reasonKey()).isEqualTo("shipping.integration.reason.allegroOnly");
        assertThat(choice.suggested(options)).map(ShippingIntegrationOption::name).contains("furgonetka");
        verify(allegro, never()).proposeShipment(any());
    }

    @Test
    void methodOnTheSellersOwnContractGreysAllegroWithTheAdaptersReason() {
        // given
        when(allegro.proposeShipment(any())).thenReturn(
                ShipmentProposal.unavailable("Metoda dostawy z Twojej umowy z przewoźnikiem — Wysyłam z Allegro jej nie nadaje."));

        // when
        ShippingIntegrationOption allegroOption = choice.forOrder(store, allegroOrder()).get(1);

        // then
        assertThat(allegroOption.available()).isFalse();
        assertThat(allegroOption.reasonKey()).isEqualTo("shipping.integration.reason.proposal");
        assertThat(allegroOption.reasonDetail()).startsWith("Metoda dostawy z Twojej umowy");
    }

    @Test
    void forbiddenAnswerMeansTheShipmentsConsentIsMissing() {
        // given
        when(allegro.proposeShipment(any())).thenThrow(
                new ShippingException("refused", new HttpClientException(403, "{\"errors\":[]}")));

        // when
        ShippingIntegrationOption allegroOption = choice.forOrder(store, allegroOrder()).get(1);

        // then
        assertThat(allegroOption.reasonKey()).isEqualTo("shipping.integration.reason.consent");
    }

    @Test
    void lostAuthorizationIsNamedAsSuch() {
        // given
        when(allegro.proposeShipment(any())).thenThrow(new HttpClientException(400, "{\"error\":\"invalid_grant\"}"));

        // when
        ShippingIntegrationOption allegroOption = choice.forOrder(store, allegroOrder()).get(1);

        // then
        assertThat(allegroOption.reasonKey()).isEqualTo("shipping.integration.reason.authLost");
    }

    @Test
    void anyOtherFailureIsATemporaryError() {
        // given
        when(allegro.proposeShipment(any())).thenThrow(new HttpClientException(503, "unavailable"));

        // when
        ShippingIntegrationOption allegroOption = choice.forOrder(store, allegroOrder()).get(1);

        // then
        assertThat(allegroOption.reasonKey()).isEqualTo("shipping.integration.reason.error");
    }

    @Test
    void storeWithAllegroOnlyHasNoDefaultOption() {
        // given
        store.removeIntegration(IntegrationType.SHIPPING_PROVIDER);
        when(allegro.proposeShipment(any())).thenReturn(proposal());

        // when
        List<ShippingIntegrationOption> options = choice.forOrder(store, allegroOrder());

        // then
        assertThat(options).extracting(ShippingIntegrationOption::name).containsExactly("allegro");
    }

    @Test
    void selectedKeepsTheRequestedAvailableOptionElseTheSuggestion() {
        // given
        when(allegro.proposeShipment(any())).thenReturn(proposal());
        List<ShippingIntegrationOption> options = choice.forOrder(store, allegroOrder());

        // then
        assertThat(ShippingIntegrationChoice.selected(options, "furgonetka")).isEqualTo("furgonetka");
        assertThat(ShippingIntegrationChoice.selected(options, null)).isEqualTo("allegro");
        assertThat(ShippingIntegrationChoice.selected(options, "unknown")).isEqualTo("allegro");
    }

    @Test
    void selectedIgnoresARequestedUnavailableOption() {
        // given
        List<ShippingIntegrationOption> options = choice.forOrder(store, shopOrder());

        // then
        assertThat(ShippingIntegrationChoice.selected(options, "allegro")).isEqualTo("furgonetka");
    }
}
