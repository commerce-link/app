package pl.commercelink.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.MessageSource;
import pl.commercelink.baskets.Basket;
import pl.commercelink.baskets.BasketsRepository;
import pl.commercelink.checkout.Checkout;
import pl.commercelink.invoicing.InvoicingService;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.ClientDataDto;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClientOfferControllerTest {

    private static final String STORE_ID = "store-1";
    private static final String OFFER_ID = "offer-1";

    @Mock
    private BasketsRepository basketsRepository;
    @Mock
    private StoresRepository storesRepository;
    @Mock
    private Checkout checkout;
    @Mock
    private InvoicingService invoicingService;
    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private ClientOfferController clientOfferController;

    @Test
    @DisplayName("submitClientOfferForm persists billing and shipping details from the submitted DTO onto the basket")
    void submitClientOfferFormPersistsBillingAndShippingDetailsFromDto() {
        // given
        Basket basket = basketBase();
        BillingDetails billing = new BillingDetails();
        billing.setCity("Krakow");
        ShippingDetails shipping = new ShippingDetails();
        shipping.setCity("Warsaw");
        ClientDataDto dto = new ClientDataDto();
        dto.setBillingDetails(billing);
        dto.setShippingDetails(shipping);
        when(basketsRepository.findById(STORE_ID, OFFER_ID)).thenReturn(Optional.of(basket));

        // when
        clientOfferController.submitClientOfferForm(STORE_ID, OFFER_ID, dto);

        // then
        ArgumentCaptor<Basket> captor = ArgumentCaptor.forClass(Basket.class);
        verify(basketsRepository).save(captor.capture());
        assertThat(captor.getValue().getBillingDetails().getCity()).isEqualTo("Krakow");
        assertThat(captor.getValue().getShippingDetails().getCity()).isEqualTo("Warsaw");
    }

    @Test
    @DisplayName("submitClientOfferForm accepts null billing and shipping details from DTO without throwing")
    void submitClientOfferFormHandlesNullBillingAndShippingDetailsGracefully() {
        // given
        Basket basket = basketBase();
        ClientDataDto dto = new ClientDataDto(); // both null
        when(basketsRepository.findById(STORE_ID, OFFER_ID)).thenReturn(Optional.of(basket));

        // when
        clientOfferController.submitClientOfferForm(STORE_ID, OFFER_ID, dto);

        // then
        ArgumentCaptor<Basket> captor = ArgumentCaptor.forClass(Basket.class);
        verify(basketsRepository).save(captor.capture());
        assertThat(captor.getValue().getBillingDetails()).isNull();
        assertThat(captor.getValue().getShippingDetails()).isNull();
    }

    private Basket basketBase() {
        Basket basket = new Basket();
        basket.setStoreId(STORE_ID);
        basket.setBasketId(OFFER_ID);
        return basket;
    }
}
