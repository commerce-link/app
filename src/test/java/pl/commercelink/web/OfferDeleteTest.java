package pl.commercelink.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import pl.commercelink.baskets.Basket;
import pl.commercelink.baskets.BasketType;
import pl.commercelink.baskets.BasketsRepository;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.web.settings.ConfirmAction;

import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OfferDeleteTest {

    private static final Locale PL = Locale.forLanguageTag("pl");

    @Mock
    private BasketsRepository basketsRepository;
    @Mock
    private MessageSource messageSource;
    @InjectMocks
    private OfferController controller;

    private MockedStatic<CustomSecurityContext> security;

    @BeforeEach
    void setup() {
        security = mockStatic(CustomSecurityContext.class);
        security.when(CustomSecurityContext::getStoreId).thenReturn("store-1");
        when(messageSource.getMessage(anyString(), any(), any(Locale.class)))
                .thenAnswer(i -> i.getArgument(0) + (i.getArgument(1) == null ? "" : java.util.Arrays.toString((Object[]) i.getArgument(1))));
    }

    @AfterEach
    void tearDown() {
        security.close();
    }

    private Basket stored(BasketType type) {
        Basket basket = new Basket();
        basket.setStoreId("store-1");
        basket.setBasketId("offer-1");
        basket.setType(type);
        basket.setName("Stacje CAD");
        when(basketsRepository.findById("store-1", "offer-1")).thenReturn(Optional.of(basket));
        return basket;
    }

    @Test
    void deletingReturnsToTheFilteredListWithANotice() {
        // given
        Basket basket = stored(BasketType.Offer);
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String view = controller.deleteOffer("offer-1", "/dashboard/offers?q=CAD&page=2", redirect, PL);

        // then
        verify(basketsRepository).delete(basket);
        assertThat(view).isEqualTo("redirect:/dashboard/offers?q=CAD&page=2");
        assertThat(redirect.getFlashAttributes().get("offerNotice")).isEqualTo("offers.deleted[Stacje CAD]");
    }

    @Test
    void deletingATemplateSaysTemplate() {
        // given
        stored(BasketType.OfferTemplate);
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        controller.deleteOffer("offer-1", null, redirect, PL);

        // then
        assertThat(redirect.getFlashAttributes().get("offerNotice")).isEqualTo("offers.template.deleted[Stacje CAD]");
    }

    private Basket unnamed(BasketType type) {
        Basket basket = stored(type);
        basket.setBasketId("offer-1");
        basket.setName(" ");
        return basket;
    }

    @Test
    void unnamedOfferIsNamedLikeInTheListInTheNoticeAndOnTheConfirmPage() {
        // given
        unnamed(BasketType.Offer);
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        controller.deleteOffer("offer-1", null, redirect, PL);
        controller.confirmDeleteOffer("offer-1", null, model, PL);

        // then
        assertThat(redirect.getFlashAttributes().get("offerNotice")).isEqualTo("offers.deleted[offers.list.untitled[offer-1]]");
        assertThat(((ConfirmAction) model.get("confirm")).title())
                .isEqualTo("offers.delete.confirm.title[offers.list.untitled[offer-1]]");
    }

    @Test
    void unnamedTemplateUsesTheTemplateWording() {
        // given
        unnamed(BasketType.OfferTemplate);
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        controller.deleteOffer("offer-1", null, redirect, PL);

        // then
        assertThat(redirect.getFlashAttributes().get("offerNotice"))
                .isEqualTo("offers.template.deleted[offers.list.untitledTemplate[offer-1]]");
    }

    @Test
    void returnToOutsideTheOffersListFallsBack() {
        // when / then
        assertThat(OfferController.safeReturnTo(null)).isEqualTo("/dashboard/offers");
        assertThat(OfferController.safeReturnTo("https://evil.example/dashboard/offers")).isEqualTo("/dashboard/offers");
        assertThat(OfferController.safeReturnTo("//evil.example")).isEqualTo("/dashboard/offers");
        assertThat(OfferController.safeReturnTo("/dashboard/orders")).isEqualTo("/dashboard/offers");
        assertThat(OfferController.safeReturnTo("/dashboard/offers\r\nSet-Cookie: x")).isEqualTo("/dashboard/offers");
        assertThat(OfferController.safeReturnTo("/dashboard/offersX")).isEqualTo("/dashboard/offers");
        assertThat(OfferController.safeReturnTo("/dashboard/offers?q={x}")).isEqualTo("/dashboard/offers");
        assertThat(OfferController.safeReturnTo("/dashboard/offers?q=%7Bx%7D}")).isEqualTo("/dashboard/offers");
        assertThat(OfferController.safeReturnTo("/dashboard/offers?segment=templates")).isEqualTo("/dashboard/offers?segment=templates");
    }

    @Test
    void anotherStoresIdIs404() {
        // given
        when(basketsRepository.findById("store-1", "foreign")).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> controller.deleteOffer("foreign", null, new RedirectAttributesModelMap(), PL))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThatThrownBy(() -> controller.confirmDeleteOffer("foreign", null, new ExtendedModelMap(), PL))
                .isInstanceOf(ResponseStatusException.class);
        verify(basketsRepository, never()).delete(any(Basket.class));
    }

    @Test
    void aStoreBasketCannotBeDeleted() {
        // given
        stored(BasketType.Basket);

        // when / then
        assertThatThrownBy(() -> controller.deleteOffer("offer-1", null, new RedirectAttributesModelMap(), PL))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void confirmationPageWithoutJavaScriptPostsToTheSameAddress() {
        // given
        stored(BasketType.Offer);
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.confirmDeleteOffer("offer-1", "/dashboard/offers?q=CAD", model, PL);

        // then
        assertThat(view).isEqualTo("settings-confirm");
        ConfirmAction confirm = (ConfirmAction) model.get("confirm");
        assertThat(confirm.actionPath()).isEqualTo("/dashboard/offer/offer-1/delete?returnTo=%2Fdashboard%2Foffers%3Fq%3DCAD");
        assertThat(confirm.cancelPath()).isEqualTo("/dashboard/offers?q=CAD");
        assertThat(confirm.destructive()).isTrue();
        assertThat(model.get("backLabel")).isEqualTo("nav.offer");
    }
}
