package pl.commercelink.shipping;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderSource;
import pl.commercelink.orders.OrderSourceType;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.orders.ShippingForm;
import pl.commercelink.shipping.api.OrderReference;
import pl.commercelink.shipping.api.ShipmentRequest;
import pl.commercelink.stores.BankAccount;
import pl.commercelink.stores.ShippingConfiguration;
import pl.commercelink.stores.Store;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AllegroShippingRequestTest {

    static ShippingDetails address(String id, String name) {
        ShippingDetails details = new ShippingDetails();
        details.setId(id);
        details.setName(name);
        details.setSurname("Kowalski");
        details.setStreetAndNumber("Magazynowa 1");
        details.setPostalCode("00-001");
        details.setCity("Warszawa");
        details.setCountry("PL");
        details.setPhone("500600700");
        return details;
    }

    static Store store() {
        Store store = new Store();
        store.setStoreId("store-1");
        ShippingConfiguration configuration = new ShippingConfiguration();
        configuration.addPickUpAddress(address("addr-1", "Magazyn"), true);
        store.setShippingConfiguration(configuration);
        BankAccount account = new BankAccount();
        account.setIban("PL61109010140000071219812874");
        account.setAccountHolder("Sklep Sp. z o.o.");
        account.set_default(true);
        store.setBankAccounts(new ArrayList<>(List.of(account)));
        return store;
    }

    static Order allegroOrder() {
        Order order = new Order("store-1");
        order.setExternalOrderId("29a9b8c0-a87a-11f1-8456-8d3ada2e8e1c");
        order.setSource(new OrderSource("Allegro", OrderSourceType.Marketplace));
        order.setShippingDetails(address(null, "Katarzyna"));
        return order;
    }

    @Test
    void allegroRequestCarriesTheOrderReferenceOneParcelAndTheCashOnDelivery() {
        // given
        ShippingForm form = new ShippingForm("order-1", "orders");
        form.setPickUpAddressId("addr-1");
        form.setCashOnDelivery(true);
        form.setCashOnDeliveryAmount(919.99);
        form.setParcels(new ArrayList<>(List.of(new ParcelForm(30, 20, 15, 2, 920, "Akcesoria", "package"))));
        Order order = allegroOrder();

        // when
        ShipmentRequest request = new ShippingService().buildAllegroRequest(form, store(), order);

        // then
        assertThat(request.orderReference())
                .isEqualTo(new OrderReference("Allegro", order.getExternalOrderId(), order.getShortenedOrderId()));
        assertThat(request.parcels()).hasSize(1);
        assertThat(request.parcels().get(0).insuranceValue()).isEqualTo(920);
        assertThat(request.options().cashOnDelivery().amount()).isEqualTo(919.99);
        assertThat(request.options().cashOnDelivery().iban()).isEqualTo("PL61109010140000071219812874");
        assertThat(request.sender().name()).contains("Magazyn");
        assertThat(request.carrierId()).isNull();
    }

    @Test
    void allegroCashOnDeliveryNeedsNoBankAccount() {
        // given: Allegro pays the collected amount out to the seller's Allegro funds
        Store store = store();
        store.setBankAccounts(new ArrayList<>());
        ShippingForm form = new ShippingForm("order-1", "orders");
        form.setPickUpAddressId("addr-1");
        form.setCashOnDelivery(true);
        form.setCashOnDeliveryAmount(919.99);
        form.setParcels(new ArrayList<>(List.of(new ParcelForm(30, 20, 15, 2, 920, "Akcesoria", "package"))));

        // when
        ShipmentRequest request = new ShippingService().buildAllegroRequest(form, store, allegroOrder());

        // then
        assertThat(request.options().cashOnDelivery().amount()).isEqualTo(919.99);
        assertThat(request.options().cashOnDelivery().iban()).isNull();
        assertThat(request.options().cashOnDelivery().accountHolder()).isNull();
    }

    @Test
    void orderReferenceIsOnlyForMarketplaceOrders() {
        // given
        Order shop = new Order("store-1");
        shop.setSource(new OrderSource("Sklep", OrderSourceType.Other));

        // when / then
        assertThat(ShippingService.orderReference(shop)).isNull();
        assertThat(ShippingService.orderReference(allegroOrder()).marketplace()).isEqualTo("Allegro");
    }

    @Test
    void withOrderReferenceKeepsEverythingElse() {
        // given
        ShipmentRequest request = ShipmentRequest.builder().carrierId("svc-1").build();
        OrderReference reference = new OrderReference("Allegro", "cf-1", "7a3f2c1e");

        // when
        ShipmentRequest withReference = ShippingService.withOrderReference(request, reference);

        // then
        assertThat(withReference.orderReference()).isEqualTo(reference);
        assertThat(withReference.carrierId()).isEqualTo("svc-1");
    }
}
