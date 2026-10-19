package pl.commercelink.web.offers;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.server.ResponseStatusException;
import pl.commercelink.baskets.Basket;
import pl.commercelink.baskets.BasketItem;
import pl.commercelink.baskets.BasketType;
import pl.commercelink.baskets.BasketsRepository;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.web.settings.ConfirmAction;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BasketViewControllerTest {

    private static final Locale PL = Locale.forLanguageTag("pl");

    @Mock
    private BasketsRepository basketsRepository;
    @Mock
    private BasketViewPageFactory pageFactory;
    @Mock
    private MessageSource messages;

    private BasketViewController controller;
    private MockedStatic<CustomSecurityContext> security;

    @BeforeEach
    void setup() {
        security = mockStatic(CustomSecurityContext.class);
        security.when(CustomSecurityContext::getStoreId).thenReturn("store-1");
        when(messages.getMessage(anyString(), any(), any(Locale.class)))
                .thenAnswer(i -> i.getArgument(0) + (i.getArgument(1) == null ? "" : java.util.Arrays.toString((Object[]) i.getArgument(1))));
        controller = new BasketViewController(basketsRepository, pageFactory, messages);
    }

    @AfterEach
    void tearDown() {
        security.close();
    }

    private Basket stored(BasketType type, boolean withItems) {
        Basket basket = new Basket();
        basket.setStoreId("store-1");
        basket.setBasketId("b-1");
        basket.setType(type);
        if (withItems) {
            basket.setBasketItems(List.of(new BasketItem("pim-1", "Product", "MFN-1", "Laptops", 100.0, 0, 1, null, 3, false)));
        }
        when(basketsRepository.findById("store-1", "b-1")).thenReturn(Optional.of(basket));
        return basket;
    }

    @Test
    void showsTheStoreBasket() {
        // given
        Basket basket = stored(BasketType.Basket, true);
        BasketViewPage page = mock(BasketViewPage.class);
        when(pageFactory.build(eq(basket), eq("/dashboard/offers?segment=baskets"), eq(PL), any(LocalDateTime.class))).thenReturn(page);
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.view("b-1", "/dashboard/offers?segment=baskets", model, PL);

        // then
        assertThat(view).isEqualTo("offers/basket");
        assertThat(model.get("page")).isSameAs(page);
    }

    @Test
    void unknownBasketIsNotFound() {
        // given
        when(basketsRepository.findById("store-1", "b-1")).thenReturn(Optional.empty());
        ExtendedModelMap model = new ExtendedModelMap();

        // when / then
        assertNotFound(() -> controller.view("b-1", null, model, PL));
        assertNotFound(() -> controller.confirmOrder("b-1", null, model, PL));
        assertNotFound(() -> controller.order("b-1", null));
    }

    @Test
    void offerOrTemplateIsNotFoundHere() {
        // given
        ExtendedModelMap model = new ExtendedModelMap();

        // when / then
        for (BasketType type : new BasketType[]{BasketType.Offer, BasketType.OfferTemplate}) {
            stored(type, true);
            assertNotFound(() -> controller.view("b-1", null, model, PL));
            assertNotFound(() -> controller.confirmOrder("b-1", null, model, PL));
            assertNotFound(() -> controller.order("b-1", null));
        }
    }

    @Test
    void confirmationIsAPrimaryChangeBackToTheBasket() {
        // given
        stored(BasketType.Basket, true);
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.confirmOrder("b-1", "/dashboard/offers?segment=baskets&q=8f", model, PL);

        // then
        assertThat(view).isEqualTo("settings-confirm");
        ConfirmAction confirm = (ConfirmAction) model.get("confirm");
        assertThat(confirm.destructive()).isFalse();
        assertThat(confirm.actionPath()).isEqualTo(
                "/dashboard/basket/view/b-1/order?returnTo=%2Fdashboard%2Foffers%3Fsegment%3Dbaskets%26q%3D8f");
        assertThat(confirm.cancelPath()).isEqualTo(
                "/dashboard/basket/view/b-1?returnTo=%2Fdashboard%2Foffers%3Fsegment%3Dbaskets%26q%3D8f");
        assertThat(model.get("backLabel")).isNotNull();
    }

    @Test
    void hostileReturnToIsDropped() {
        // given
        stored(BasketType.Basket, true);
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        controller.confirmOrder("b-1", "https://evil.example", model, PL);

        // then
        ConfirmAction confirm = (ConfirmAction) model.get("confirm");
        assertThat(confirm.actionPath()).isEqualTo("/dashboard/basket/view/b-1/order");
        assertThat(confirm.cancelPath()).isEqualTo("/dashboard/basket/view/b-1");
    }

    @Test
    void orderHandsOverToTheOrderFormWithoutSaving() {
        // given
        stored(BasketType.Basket, true);

        // when
        String view = controller.order("b-1", null);

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/orders/new/from-basket?basketId=b-1");
        verify(basketsRepository, never()).save(any());
        verify(basketsRepository, never()).delete(any(Basket.class));
    }

    @Test
    void emptyBasketGoesBackInsteadOfOrdering() {
        // given
        stored(BasketType.Basket, false);

        // when
        String order = controller.order("b-1", null);
        String confirm = controller.confirmOrder("b-1", null, new ExtendedModelMap(), PL);

        // then
        assertThat(order).isEqualTo("redirect:/dashboard/basket/view/b-1");
        assertThat(confirm).isEqualTo("redirect:/dashboard/basket/view/b-1");
    }

    @Test
    void returnToQueryEncodesOnlySafeNonDefaultAddresses() {
        // when / then
        assertThat(BasketViewPageFactory.returnToQuery(null)).isEmpty();
        assertThat(BasketViewPageFactory.returnToQuery("https://evil.example")).isEmpty();
        assertThat(BasketViewPageFactory.returnToQuery(BasketViewPageFactory.BASKETS_PATH)).isEmpty();
        assertThat(BasketViewPageFactory.returnToQuery("/dashboard/offers?segment=baskets&q=8f"))
                .isEqualTo("?returnTo=%2Fdashboard%2Foffers%3Fsegment%3Dbaskets%26q%3D8f");
    }

    private void assertNotFound(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
