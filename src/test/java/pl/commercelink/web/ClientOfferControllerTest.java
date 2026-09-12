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
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import pl.commercelink.baskets.Basket;
import pl.commercelink.baskets.BasketItem;
import pl.commercelink.baskets.BasketsRepository;
import pl.commercelink.checkout.Checkout;
import pl.commercelink.invoicing.InvoicingService;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.ClientDataDto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
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

    @Test
    @DisplayName("submitClientOfferForm redirects to the client offer path for an offer created after the cutoff date")
    void submitClientOfferFormRedirectsToClientOfferPathForNewOffer() {
        // given
        Basket basket = basketBase();
        basket.setCreatedAt(LocalDateTime.of(2026, 9, 10, 8, 0));
        when(basketsRepository.findById(STORE_ID, OFFER_ID)).thenReturn(Optional.of(basket));

        // when
        String view = clientOfferController.submitClientOfferForm(STORE_ID, OFFER_ID, new ClientDataDto());

        // then
        assertThat(view).isEqualTo("redirect:/store/store-1/client/offer/offer-1");
    }

    @Test
    @DisplayName("submitClientOfferForm redirects to the individual offer path for an offer created before the cutoff date")
    void submitClientOfferFormRedirectsToIndividualOfferPathForOldOffer() {
        // given
        Basket basket = basketBase();
        basket.setCreatedAt(LocalDateTime.of(2026, 9, 9, 8, 0));
        when(basketsRepository.findById(STORE_ID, OFFER_ID)).thenReturn(Optional.of(basket));

        // when
        String view = clientOfferController.submitClientOfferForm(STORE_ID, OFFER_ID, new ClientDataDto());

        // then
        assertThat(view).isEqualTo("redirect:/store/store-1/individual/offer/offer-1");
    }

    @Test
    @DisplayName("createProformaInvoice redirects back to the offer path matching the offer creation date")
    void createProformaInvoiceRedirectsToOfferPathMatchingCreationDate() {
        // given
        Basket basket = basketBase();
        basket.setCreatedAt(LocalDateTime.of(2026, 9, 12, 8, 0));
        when(basketsRepository.findById(STORE_ID, OFFER_ID)).thenReturn(Optional.of(basket));
        when(invoicingService.createProforma(basket, Locale.ENGLISH, true)).thenReturn(new InvoicingService.OperationResult("inv-1", "PF/1", null, null));

        // when
        String view = clientOfferController.createProformaInvoice(STORE_ID, OFFER_ID, Locale.ENGLISH, new RedirectAttributesModelMap());

        // then
        assertThat(view).isEqualTo("redirect:/store/store-1/client/offer/offer-1");
    }

    @Test
    @DisplayName("selectVariant marks the chosen item within its group, saves the basket and redirects back to the offer")
    void selectVariantMarksChosenItemSavesAndRedirects() {
        // given
        Basket basket = basketBase();
        basket.setCreatedAt(LocalDateTime.of(2026, 9, 12, 8, 0));
        BasketItem ssd1 = variant("MFN-SSD-1");
        BasketItem ssd2 = variant("MFN-SSD-2");
        basket.setBasketItems(List.of(ssd1, ssd2));
        when(basketsRepository.findById(STORE_ID, OFFER_ID)).thenReturn(Optional.of(basket));

        // when
        String view = clientOfferController.selectVariant(STORE_ID, OFFER_ID, "SSD", ssd2.getPosition());

        // then
        verify(basketsRepository).save(basket);
        assertThat(ssd1.isVariantSelected()).isFalse();
        assertThat(ssd2.isVariantSelected()).isTrue();
        assertThat(view).isEqualTo("redirect:/store/store-1/client/offer/offer-1");
    }

    private BasketItem variant(String mfn) {
        BasketItem item = new BasketItem("pim-1", "SSD " + mfn, mfn, "SSD", 100.0, 0, 1, null, 3, false);
        item.setVariantGroupId("SSD");
        return item;
    }

    private Basket basketBase() {
        Basket basket = new Basket();
        basket.setStoreId(STORE_ID);
        basket.setBasketId(OFFER_ID);
        return basket;
    }
}
