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
import pl.commercelink.baskets.Basket;
import pl.commercelink.baskets.BasketType;
import pl.commercelink.baskets.BasketsRepository;
import pl.commercelink.baskets.ContactDetails;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.starter.security.CustomSecurityContext;

import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OfferContactPrefillTest {

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
        basket.setBasketId("basket-12345678");
        basket.setType(type);
        when(basketsRepository.findById("store-1", "basket-12345678")).thenReturn(Optional.of(basket));
        return basket;
    }

    @Test
    void contactStepFromAStoreBasketStartsFromItsBillingDetails() {
        // given
        Basket basket = stored(BasketType.Basket);
        BillingDetails billing = new BillingDetails();
        billing.setName("Jan");
        billing.setSurname("Kowalski");
        billing.setCompanyName("Acme");
        billing.setEmail("jan@acme.pl");
        ShippingDetails shipping = new ShippingDetails();
        shipping.setName("Adam");
        shipping.setPhone(" 500100200 ");
        basket.setBillingDetails(billing);
        basket.setShippingDetails(shipping);
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.showContactDetailsForm("copy", "basket-12345678", model, PL);

        // then
        ContactDetails contact = (ContactDetails) model.get("contactDetails");
        assertThat(view).isEqualTo("offerContact");
        assertThat(contact.getName()).isEqualTo("Jan");
        assertThat(contact.getSurname()).isEqualTo("Kowalski");
        assertThat(contact.getCompanyName()).isEqualTo("Acme");
        assertThat(contact.getEmail()).isEqualTo("jan@acme.pl");
        assertThat(contact.getPhone()).isEqualTo("500100200");
        assertThat(model.get("offerName")).isEqualTo("offers.basket.offerName[" + basket.getShortenedBasketId() + "]");
    }

    @Test
    void contactStepFromAnOfferStaysEmpty() {
        // given
        Basket offer = stored(BasketType.Offer);
        BillingDetails billing = new BillingDetails();
        billing.setName("Jan");
        offer.setBillingDetails(billing);
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        controller.showContactDetailsForm("copy", "basket-12345678", model, PL);

        // then
        ContactDetails contact = (ContactDetails) model.get("contactDetails");
        assertThat(contact.getName()).isNull();
        assertThat(model.get("offerName")).isNull();
    }

    @Test
    void contactStepFromAnUnknownSourceAnswersNotFound() {
        // given
        when(basketsRepository.findById("store-1", "missing")).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> controller.showContactDetailsForm("copy", "missing", new ExtendedModelMap(), PL))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void manualOfferStartsEmpty() {
        // given
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        controller.showContactDetailsForm("manual", null, model, PL);

        // then
        verifyNoInteractions(basketsRepository);
        assertThat(model.get("offerName")).isNull();
        assertThat(((ContactDetails) model.get("contactDetails")).getName()).isNull();
    }
}
