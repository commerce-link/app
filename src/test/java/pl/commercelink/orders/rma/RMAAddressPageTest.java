package pl.commercelink.orders.rma;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.web.rma.RmaAddressPage;

import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The return address page of an RMA: GET /dashboard/rma/{id}/shipping and its save. */
@ExtendWith(MockitoExtension.class)
class RMAAddressPageTest {

    private static final String STORE_ID = "store-1";
    private static final String RMA_ID = "rma-123456789";
    private static final String ORDER_ID = "order-987654321";

    @Mock private RMARepository rmaRepository;
    @Mock private OrdersRepository ordersRepository;
    @Mock private MessageSource messageSource;
    @Mock private RedirectAttributes redirectAttributes;
    // the remaining collaborators of RMAController are not touched by these endpoints
    @Mock private MarketplaceReturnDecisions marketplaceReturnDecisions;
    @Mock private RMAItemsRepository rmaItemsRepository;
    @Mock private RMALifecycle rmaLifecycle;
    @Mock private RMAManager rmaManager;
    @Mock private pl.commercelink.orders.OrderItemsRepository orderItemsRepository;
    @Mock private pl.commercelink.orders.OrdersRMAManager ordersRMAManager;
    @Mock private pl.commercelink.starter.storage.FileStorage fileStorage;
    @Mock private OpenRmaCoverage openRmaCoverage;
    @Mock private RmaShipmentsViewFactory rmaShipmentsViewFactory;

    @InjectMocks
    private RMAController controller;

    private static ShippingDetails address(String city) {
        ShippingDetails d = new ShippingDetails();
        d.setName("Jan");
        d.setStreetAndNumber("Długa 1");
        d.setPostalCode("00-001");
        d.setCity(city);
        d.setCountry("PL");
        d.setEmail("jan@example.com");
        d.setPhone("600700800");
        return d;
    }

    private static RMA openRma(ShippingDetails address) {
        RMA rma = new RMA(STORE_ID);
        rma.setRmaId(RMA_ID);
        rma.setOrderId(ORDER_ID);
        rma.setStatus(RMAStatus.New);
        rma.setShippingDetails(address);
        return rma;
    }

    private static Order orderWith(ShippingDetails address) {
        Order order = new Order();
        order.setOrderId(ORDER_ID);
        order.setShippingDetails(address);
        return order;
    }

    private String get(String fill, ExtendedModelMap model) {
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            return controller.editRmaShipping(RMA_ID, fill, model, redirectAttributes, Locale.ENGLISH);
        }
    }

    private String post(RMA posted, ExtendedModelMap model) {
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            return controller.updateShippingDetails(RMA_ID, posted, model, redirectAttributes, Locale.ENGLISH);
        }
    }

    @Test
    void showsTheSavedAddressAndOffersADifferentOrderAddress() {
        // given
        when(rmaRepository.findById(STORE_ID, RMA_ID)).thenReturn(openRma(address("Warszawa")));
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(orderWith(address("Gdańsk")));
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = get(null, model);

        // then
        assertThat(view).isEqualTo("rma/address");
        RmaAddressPage page = (RmaAddressPage) model.getAttribute("page");
        assertThat(page.form().city()).isEqualTo("Warszawa");
        assertThat(page.rmaShortId()).isEqualTo("rma-1234");
        assertThat(page.fillAvailable()).isTrue();
        assertThat(page.filled()).isFalse();
        assertThat(page.missing()).isFalse();
    }

    @Test
    void doesNotOfferTheOrderAddressWhenItIsTheSame() {
        // given
        when(rmaRepository.findById(STORE_ID, RMA_ID)).thenReturn(openRma(address("Warszawa")));
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(orderWith(address("Warszawa")));
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        get(null, model);

        // then
        assertThat(((RmaAddressPage) model.getAttribute("page")).fillAvailable()).isFalse();
    }

    @Test
    void fillsTheFormWithTheOrderAddressWithoutSaving() {
        // given
        when(rmaRepository.findById(STORE_ID, RMA_ID)).thenReturn(openRma(null));
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(orderWith(address("Gdańsk")));
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        get("order", model);

        // then
        RmaAddressPage page = (RmaAddressPage) model.getAttribute("page");
        assertThat(page.form().city()).isEqualTo("Gdańsk");
        assertThat(page.filled()).isTrue();
        assertThat(page.fillAvailable()).isFalse();
        assertThat(page.orderShortId()).isEqualTo("order-98");
        verify(rmaRepository, never()).save(any());
    }

    @Test
    void aMissingOrderLeavesThePageWithoutTheOffer() {
        // given
        when(rmaRepository.findById(STORE_ID, RMA_ID)).thenReturn(openRma(null));
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(null);
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = get("order", model);

        // then
        assertThat(view).isEqualTo("rma/address");
        RmaAddressPage page = (RmaAddressPage) model.getAttribute("page");
        assertThat(page.fillAvailable()).isFalse();
        assertThat(page.filled()).isFalse();
        assertThat(page.missing()).isTrue();
    }

    @Test
    void anOrderWithoutAnAddressIsNotOffered() {
        // given
        when(rmaRepository.findById(STORE_ID, RMA_ID)).thenReturn(openRma(null));
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(orderWith(ShippingDetails._default()));
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        get("order", model);

        // then
        RmaAddressPage page = (RmaAddressPage) model.getAttribute("page");
        assertThat(page.fillAvailable()).isFalse();
        assertThat(page.filled()).isFalse();
    }

    @Test
    void anUnknownOrForeignRmaGoesBackToTheList() {
        // given
        when(rmaRepository.findById(STORE_ID, RMA_ID)).thenReturn(null);
        when(messageSource.getMessage(eq("rma.not.found"), any(), any())).thenReturn("not found");

        // when
        String view = get(null, new ExtendedModelMap());

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/rma");
        verify(redirectAttributes).addFlashAttribute("errorMessage", "not found");
    }

    @Test
    void aClosedRmaGoesBackToItsPage() {
        // given
        RMA closed = openRma(address("Warszawa"));
        closed.setStatus(RMAStatus.Completed);
        when(rmaRepository.findById(STORE_ID, RMA_ID)).thenReturn(closed);
        when(messageSource.getMessage(eq("rma.already.closed"), any(), any())).thenReturn("closed");

        // when
        String view = get(null, new ExtendedModelMap());

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/rma/" + RMA_ID);
        verify(redirectAttributes).addFlashAttribute("errorMessage", "closed");
    }

    @Test
    void comingFromShipToClientShowsEveryMissingField() {
        // given
        ShippingDetails partial = address("Warszawa");
        partial.setPhone(null);
        when(rmaRepository.findById(STORE_ID, RMA_ID)).thenReturn(openRma(partial));
        ExtendedModelMap model = new ExtendedModelMap();
        model.addAttribute(RMAController.ADDRESS_INCOMPLETE_FLASH, true);

        // when
        get(null, model);

        // then
        RmaAddressPage page = (RmaAddressPage) model.getAttribute("page");
        assertThat(page.incompleteForShipping()).isTrue();
        assertThat(page.form().errors()).containsOnlyKeys("shippingDetails.phone");
    }

    @Test
    void comingFromShipToClientDoesNotFlagTheFormatOfUntouchedValues() {
        // given
        ShippingDetails partial = address("Warszawa");
        partial.setCountry("Polska");
        partial.setPhone(null);
        when(rmaRepository.findById(STORE_ID, RMA_ID)).thenReturn(openRma(partial));
        ExtendedModelMap model = new ExtendedModelMap();
        model.addAttribute(RMAController.ADDRESS_INCOMPLETE_FLASH, true);

        // when
        get(null, model);

        // then
        RmaAddressPage page = (RmaAddressPage) model.getAttribute("page");
        assertThat(page.form().errors()).containsOnlyKeys("shippingDetails.phone");
    }

    @Test
    void aBlankOrderIdLooksUpNoOrder() {
        // given
        RMA rma = openRma(address("Warszawa"));
        rma.setOrderId("");
        when(rmaRepository.findById(STORE_ID, RMA_ID)).thenReturn(rma);
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = get(null, model);

        // then
        assertThat(view).isEqualTo("rma/address");
        RmaAddressPage page = (RmaAddressPage) model.getAttribute("page");
        assertThat(page.fillAvailable()).isFalse();
        verify(ordersRepository, never()).findById(any(), any());
    }

    @Test
    void anInvalidSaveRendersTheTypedValuesWithErrorsAndSavesNothing() {
        // given
        when(rmaRepository.findById(STORE_ID, RMA_ID)).thenReturn(openRma(address("Warszawa")));
        RMA posted = new RMA(STORE_ID);
        ShippingDetails typed = address("Kraków");
        typed.setPhone("12");
        posted.setShippingDetails(typed);
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = post(posted, model);

        // then
        assertThat(view).isEqualTo("rma/address");
        RmaAddressPage page = (RmaAddressPage) model.getAttribute("page");
        assertThat(page.form().city()).isEqualTo("Kraków");
        assertThat(page.form().errors()).isEqualTo(Map.of("shippingDetails.phone", "billing.phone.invalid"));
        verify(rmaRepository, never()).save(any());
    }

    @Test
    void aValidSaveStoresOnlyTheAddressAndReturnsToTheRma() {
        // given
        ShippingDetails saved = address("Warszawa");
        saved.setId("addr-1");
        RMA existing = openRma(saved);
        existing.setEmail("buyer@example.com");
        when(rmaRepository.findById(STORE_ID, RMA_ID)).thenReturn(existing);
        when(messageSource.getMessage(eq("rma.address.saved"), any(), any())).thenReturn("saved");
        RMA posted = new RMA(STORE_ID);
        posted.setShippingDetails(address(" Kraków "));

        // when
        String view = post(posted, new ExtendedModelMap());

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/rma/" + RMA_ID);
        verify(rmaRepository).save(existing);
        assertThat(existing.getShippingDetails().getCity()).isEqualTo("Kraków");
        assertThat(existing.getShippingDetails().getId()).isEqualTo("addr-1");
        assertThat(existing.getEmail()).isEqualTo("buyer@example.com");
        verify(redirectAttributes).addFlashAttribute("successMessage", "saved");
    }
}
