package pl.commercelink.baskets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import pl.commercelink.checkout.CheckoutError;
import pl.commercelink.checkout.CheckoutException;
import pl.commercelink.checkout.CheckoutRequest;
import pl.commercelink.discounts.DiscountCode;
import pl.commercelink.discounts.DiscountCodesRepository;
import pl.commercelink.discounts.DiscountService;
import pl.commercelink.discounts.DiscountTarget;
import pl.commercelink.discounts.DiscountType;
import pl.commercelink.discounts.TargetValue;
import pl.commercelink.invoicing.InvoicingService;
import pl.commercelink.pricelist.PricelistRepository;
import pl.commercelink.stores.PaymentIntegration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BasketsRestApiTest {

    private static final String STORE_ID = "store-1";
    private static final String CODE = "LATO-10";
    private static final String DEFAULT_PROVIDER = "paynow";
    private static final String OTHER_PROVIDER = "przelewy24";

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private BasketsRepository basketsRepository;
    @Mock
    private InvoicingService invoicingService;
    @Mock
    private PricelistRepository pricelistRepository;
    @Mock
    private DiscountCodesRepository discountCodesRepository;
    @Mock
    private CheckoutRequest req;
    @Mock
    private Store store;
    @Captor
    private ArgumentCaptor<Basket> savedBasket;

    @InjectMocks
    private BasketsRestApi basketsRestApi;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(basketsRestApi, "discountService", new DiscountService(discountCodesRepository));
        when(store.getStoreId()).thenReturn(STORE_ID);
        when(store.getDefaultPaymentIntegration()).thenReturn(Optional.of(new PaymentIntegration(DEFAULT_PROVIDER, true)));
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(req.toBasketItems(STORE_ID, pricelistRepository)).thenReturn(Collections.emptyList());
    }

    @Test
    @DisplayName("createBasket creates proforma with configured fallback locale instead of JVM default")
    void createBasketCreatesProformaWithConfiguredFallbackLocale() {
        // given
        ReflectionTestUtils.setField(basketsRestApi, "fallbackLocale", Locale.GERMAN);
        when(req.isSendInvoice()).thenReturn(true);
        InvoicingService.OperationResult result = mock(InvoicingService.OperationResult.class);
        when(invoicingService.createProforma(any(Basket.class), any(Locale.class), eq(true))).thenReturn(result);

        // when
        basketsRestApi.createBasket(req, STORE_ID);

        // then
        ArgumentCaptor<Locale> localeCaptor = ArgumentCaptor.forClass(Locale.class);
        verify(invoicingService).createProforma(any(Basket.class), localeCaptor.capture(), eq(true));
        assertThat(localeCaptor.getValue()).isEqualTo(Locale.GERMAN);
    }

    @Test
    void basketWithCodeIsSavedDiscountedAndListsBothPrices() {
        // given
        givenItems();
        givenCode(percentageOnAcme());
        when(req.getDiscountCode()).thenReturn(" lato-10 ");

        // when
        CreatedBasketResponse response = basketsRestApi.createBasket(req, STORE_ID).getBody();

        // then
        verify(basketsRepository).save(savedBasket.capture());
        assertThat(savedBasket.getValue().getDiscountCode()).isEqualTo(CODE);
        assertThat(savedBasket.getValue().getDiscountAmount()).isEqualTo(100.00);
        assertThat(savedBasket.getValue().getBasketItems()).extracting(BasketItem::getUnitPrice).containsExactly(900.00, 100.00);
        assertThat(response.id()).isEqualTo(savedBasket.getValue().getBasketId());
        assertThat(response.items())
                .extracting(CreatedBasketResponse.Item::itemId, CreatedBasketResponse.Item::catalogId,
                        CreatedBasketResponse.Item::qty, CreatedBasketResponse.Item::unitPrice,
                        CreatedBasketResponse.Item::discountedUnitPrice)
                .containsExactly(tuple("pim-gpu", "catalog-1", 1L, 1000.00, 900.00),
                        tuple("pim-ram", "catalog-1", 2L, 100.00, 100.00));
        assertThat(response.discountCode()).isEqualTo(CODE);
        assertThat(response.discountAmount()).isEqualTo(100.00);
        assertThat(response.totalPrice()).isEqualTo(1200.00);
        assertThat(response.discountedTotalPrice()).isEqualTo(1100.00);
    }

    @Test
    void basketWithoutCodeListsUnchangedPrices() {
        // given
        givenItems();
        when(req.getDiscountCode()).thenReturn("  ");

        // when
        CreatedBasketResponse response = basketsRestApi.createBasket(req, STORE_ID).getBody();

        // then
        verify(basketsRepository).save(any(Basket.class));
        verify(discountCodesRepository, never()).find(any(), any());
        assertThat(response.items()).extracting(CreatedBasketResponse.Item::unitPrice, CreatedBasketResponse.Item::discountedUnitPrice)
                .containsExactly(tuple(1000.00, 1000.00), tuple(100.00, 100.00));
        assertThat(response.discountCode()).isNull();
        assertThat(response.discountAmount()).isZero();
        assertThat(response.totalPrice()).isEqualTo(1200.00);
        assertThat(response.discountedTotalPrice()).isEqualTo(1200.00);
    }

    @Test
    void rejectedCodeCreatesNeitherBasketNorProforma() {
        // given
        givenItems();
        when(discountCodesRepository.find(STORE_ID, CODE)).thenReturn(Optional.empty());
        when(req.getDiscountCode()).thenReturn(CODE);
        when(req.isSendInvoice()).thenReturn(true);

        // when / then
        assertThatThrownBy(() -> basketsRestApi.createBasket(req, STORE_ID))
                .isInstanceOfSatisfying(CheckoutException.class,
                        e -> assertThat(e.getError()).isEqualTo(CheckoutError.DiscountCodeNotFound));
        verify(basketsRepository, never()).save(any());
        verify(invoicingService, never()).createProforma(any(), any(), anyBoolean());
    }

    @Test
    void codeIsCheckedAgainstTheChosenPaymentOption() {
        // given
        givenItems();
        DiscountCode code = percentageOnAcme();
        code.setPaymentProvider(OTHER_PROVIDER);
        givenCode(code);
        when(req.getDiscountCode()).thenReturn(CODE);
        when(req.getPaymentOptionId()).thenReturn(OTHER_PROVIDER);

        // when
        CreatedBasketResponse response = basketsRestApi.createBasket(req, STORE_ID).getBody();

        // then
        assertThat(response.discountCode()).isEqualTo(CODE);
    }

    @Test
    void basketWithoutPaymentOptionChecksTheDefaultGateway() {
        // given
        givenItems();
        DiscountCode code = percentageOnAcme();
        code.setPaymentProvider(OTHER_PROVIDER);
        givenCode(code);
        when(req.getDiscountCode()).thenReturn(CODE);

        // when / then
        assertThatThrownBy(() -> basketsRestApi.createBasket(req, STORE_ID))
                .isInstanceOfSatisfying(CheckoutException.class,
                        e -> assertThat(e.getError()).isEqualTo(CheckoutError.DiscountCodePaymentMethodMismatch));
        verify(basketsRepository, never()).save(any());
    }

    @Test
    void proformaIsIssuedForTheDiscountedBasket() {
        // given
        givenItems();
        givenCode(percentageOnAcme());
        when(req.getDiscountCode()).thenReturn(CODE);
        when(req.isSendInvoice()).thenReturn(true);
        InvoicingService.OperationResult result = mock(InvoicingService.OperationResult.class);
        when(result.getInvoiceNo()).thenReturn("PRO/1/10/2026");
        ArgumentCaptor<Basket> invoiced = ArgumentCaptor.forClass(Basket.class);
        when(invoicingService.createProforma(invoiced.capture(), any(Locale.class), eq(true))).thenReturn(result);

        // when
        CreatedBasketResponse response = basketsRestApi.createBasket(req, STORE_ID).getBody();

        // then
        assertThat(invoiced.getValue().getTotalPrice()).isEqualTo(1100.00);
        assertThat(response.invoiceNo()).isEqualTo("PRO/1/10/2026");
    }

    @Test
    void responseJsonKeepsIdAndInvoiceNoAndListsPrices() throws Exception {
        // given
        givenItems();
        givenCode(percentageOnAcme());
        when(req.getDiscountCode()).thenReturn(CODE);
        CreatedBasketResponse response = basketsRestApi.createBasket(req, STORE_ID).getBody();

        // when
        JsonNode json = new ObjectMapper().valueToTree(response);

        // then
        assertThat(json.fieldNames()).toIterable().containsExactlyInAnyOrder("id", "invoiceNo", "items", "discountCode",
                "discountAmount", "totalPrice", "discountedTotalPrice");
        assertThat(json.get("items").get(0).fieldNames()).toIterable()
                .containsExactlyInAnyOrder("itemId", "catalogId", "qty", "unitPrice", "discountedUnitPrice");
        assertThat(json.get("items").get(0).get("discountedUnitPrice").asDouble()).isEqualTo(900.00);
    }

    private void givenItems() {
        BasketItem gpu = new BasketItem("pim-gpu", "Karta graficzna", "MFN-GPU", "Karty graficzne", 1000, 0, 1, "catalog-1", 2, false);
        gpu.setBrand("Acme");
        BasketItem ram = new BasketItem("pim-ram", "Pamięć RAM", "MFN-RAM", "Pamięci", 100, 0, 2, "catalog-1", 2, false);
        ram.setBrand("Globex");
        when(req.toBasketItems(STORE_ID, pricelistRepository)).thenReturn(List.of(gpu, ram));
    }

    private void givenCode(DiscountCode code) {
        when(discountCodesRepository.find(STORE_ID, CODE)).thenReturn(Optional.of(code));
    }

    private static DiscountCode percentageOnAcme() {
        DiscountCode code = new DiscountCode();
        code.setStoreId(STORE_ID);
        code.setCode(CODE);
        code.setName("Kampania letnia");
        code.setActive(true);
        code.setType(DiscountType.Percentage);
        code.setValue(10);
        code.setTarget(DiscountTarget.Brand);
        code.setTargetValues(List.of(new TargetValue("Acme", "Acme")));
        return code;
    }
}
