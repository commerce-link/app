package pl.commercelink.orders.rma;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.context.MessageSource;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.inventory.deliveries.DeliveredPredicate;
import pl.commercelink.inventory.deliveries.DeliveriesRepository;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.orders.ShippingForm;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Starting a shipment from the RMA page: the items form of rma-detail.html posts only the checkbox and the
 * identifiers of each item (selected, rmaId, rmaItemId, itemId, tax), never its status or delivery, so the
 * controller has to judge the selected items by their stored state.
 */
@ExtendWith(MockitoExtension.class)
class RMAShippingStartTest {

    private static final String STORE_ID = "store-1";
    private static final String RMA_ID = "rma-1";

    @Mock
    private RMARepository rmaRepository;
    @Mock
    private RMAItemsRepository rmaItemsRepository;
    @Mock
    private DeliveredPredicate deliveredPredicate;
    @Mock
    private DeliveriesRepository deliveriesRepository;
    @Mock
    private RMACentersRepository rmaCentersRepository;
    @Mock
    private StoresRepository storesRepository;
    @Mock
    private MessageSource messageSource;
    @Mock
    private RedirectAttributes redirectAttributes;

    @InjectMocks
    private RMAShippingController controller;

    @Test
    void shipToClientOpensTheShippingScreenForAReceivedItemSelectedOnTheRmaPage() {
        // given
        RMA rma = rmaWithClientAddress();
        when(rmaRepository.findById(STORE_ID, RMA_ID)).thenReturn(rma);
        when(rmaItemsRepository.findByRmaId(RMA_ID)).thenReturn(List.of(
                storedItem("rma-item-1", "order-item-1", RMAItemStatus.Received),
                storedItem("rma-item-2", "order-item-2", RMAItemStatus.Received)));
        when(storesRepository.findById(STORE_ID)).thenReturn(new Store());
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view;
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            view = controller.initiateShippingToClient(RMA_ID, postedRmaPageForm(), redirectAttributes, model, Locale.ENGLISH);
        }

        // then
        assertThat(view).isEqualTo("shipping");
        ShippingForm shippingForm = (ShippingForm) model.getAttribute("shippingForm");
        assertThat(shippingForm.isToClient()).isTrue();
        assertThat(shippingForm.getOrderItemIds()).containsExactly("order-item-1");
        assertThat(shippingForm.getShippingDetails()).isSameAs(rma.getShippingDetails());
    }

    @Test
    void shipToDistributorOpensTheShippingScreenWithTheServiceCentresOfTheStoredDelivery() {
        // given
        when(rmaRepository.findById(STORE_ID, RMA_ID)).thenReturn(rmaWithClientAddress());
        when(rmaItemsRepository.findByRmaId(RMA_ID)).thenReturn(List.of(
                storedItem("rma-item-1", "order-item-1", RMAItemStatus.Received),
                storedItem("rma-item-2", "order-item-2", RMAItemStatus.Received)));
        when(deliveredPredicate.isFromSameSource(eq(STORE_ID), anyList())).thenReturn(true);
        when(deliveriesRepository.findById(STORE_ID, "delivery-1")).thenReturn(new Delivery(STORE_ID, "ext-1", "Elko"));
        RMACenter centre = new RMACenter();
        ShippingDetails centreAddress = new ShippingDetails();
        centre.setShippingDetails(centreAddress);
        when(rmaCentersRepository.findByProviderName(STORE_ID, "Elko")).thenReturn(List.of(centre));
        when(storesRepository.findById(STORE_ID)).thenReturn(new Store());
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view;
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            view = controller.initiateShippingToDistributor(RMA_ID, postedRmaPageForm(), model, redirectAttributes, Locale.ENGLISH);
        }

        // then
        assertThat(view).isEqualTo("shipping");
        ShippingForm shippingForm = (ShippingForm) model.getAttribute("shippingForm");
        assertThat(shippingForm.isToClient()).isFalse();
        assertThat(shippingForm.getOrderItemIds()).containsExactly("order-item-1");
        assertThat(model.getAttribute("shippingDetailsList")).isEqualTo(List.of(centreAddress));
    }

    @Test
    void shipToClientIsStillRefusedWhenTheSelectedItemHasNotBeenReceived() {
        // given
        when(rmaRepository.findById(STORE_ID, RMA_ID)).thenReturn(rmaWithClientAddress());
        when(rmaItemsRepository.findByRmaId(RMA_ID)).thenReturn(List.of(
                storedItem("rma-item-1", "order-item-1", RMAItemStatus.New)));
        when(messageSource.getMessage(eq("rma.cannot.ship.to.client.invalid.status"), any(), any())).thenReturn("refused");

        // when
        String view;
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            view = controller.initiateShippingToClient(RMA_ID, postedRmaPageForm(), redirectAttributes, new ExtendedModelMap(), Locale.ENGLISH);
        }

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/rma/" + RMA_ID);
    }

    /** The request the RMA page sends when the operator ticks the first of two items. */
    private static RMAItemsForm postedRmaPageForm() {
        RMAItemsForm form = new RMAItemsForm();
        WebDataBinder binder = new WebDataBinder(form);
        binder.bind(new MutablePropertyValues(Map.of(
                "rmaItems[0].selected", "true",
                "_rmaItems[0].selected", "on",
                "rmaItems[0].rmaId", RMA_ID,
                "rmaItems[0].rmaItemId", "rma-item-1",
                "rmaItems[0].itemId", "order-item-1",
                "rmaItems[0].tax", "23.0",
                "_rmaItems[1].selected", "on",
                "rmaItems[1].rmaId", RMA_ID,
                "rmaItems[1].rmaItemId", "rma-item-2",
                "rmaItems[1].itemId", "order-item-2")));
        return form;
    }

    private static RMAItem storedItem(String rmaItemId, String itemId, RMAItemStatus status) {
        RMAItem item = new RMAItem();
        item.setRmaId(RMA_ID);
        item.setRmaItemId(rmaItemId);
        item.setItemId(itemId);
        item.setStatus(status);
        item.setDeliveryId("delivery-1");
        return item;
    }

    private static RMA rmaWithClientAddress() {
        RMA rma = new RMA(STORE_ID);
        rma.setRmaId(RMA_ID);
        ShippingDetails address = new ShippingDetails();
        address.setName("Jan");
        address.setSurname("Kowalski");
        address.setStreetAndNumber("Prosta 1");
        address.setPostalCode("00-001");
        address.setCity("Warszawa");
        address.setCountry("PL");
        address.setEmail("jan@example.com");
        address.setPhone("500600700");
        rma.setShippingDetails(address);
        return rma;
    }
}
