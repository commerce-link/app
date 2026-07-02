package pl.commercelink.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.MessageSource;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import pl.commercelink.baskets.Basket;
import pl.commercelink.baskets.BasketItem;
import pl.commercelink.baskets.BasketsRepository;
import pl.commercelink.baskets.OfferItemReloader;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.invoicing.InvoicingService;
import pl.commercelink.offer.imports.OfferImporter;
import pl.commercelink.pricelist.AvailabilityAndPrice;
import pl.commercelink.pricelist.Pricelist;
import pl.commercelink.pricelist.PricelistRepository;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.taxonomy.ProductCategory;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OfferControllerTest {

    private static final String STORE_ID = "store-1";
    private static final String OFFER_ID = "offer-1";

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private BasketsRepository basketsRepository;
    @Mock
    private ProductCatalogRepository productCatalogRepository;
    @Mock
    private PricelistRepository pricelistRepository;
    @Mock
    private OfferItemReloader offerItemReloader;
    @Mock
    private Inventory inventory;
    @Mock
    private InventoryView inventoryView;
    @Mock
    private InvoicingService invoicingService;
    @Mock
    private List<OfferImporter> offerImporters;
    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private OfferController offerController;

    private MockedStatic<CustomSecurityContext> securityStub;

    @BeforeEach
    void setupStoreId() {
        securityStub = mockStatic(CustomSecurityContext.class);
        securityStub.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
        securityStub.when(CustomSecurityContext::getLoggedInUserName).thenReturn("test-user");
    }

    @AfterEach
    void tearDown() {
        securityStub.close();
    }

    @Test
    @DisplayName("updateOffer applies all settable fields from request payload to the existing basket and persists")
    void updateOfferAppliesAllSettersFromRequestPayloadToExistingBasket() {
        // given
        Basket existing = basketBase();
        Basket payload = new Basket();
        payload.setName("Updated Name");
        payload.setComment("Updated comment");
        payload.setShowPrices(true);
        when(basketsRepository.findById(STORE_ID, OFFER_ID)).thenReturn(Optional.of(existing));

        // when
        offerController.updateOffer(OFFER_ID, payload);

        // then
        ArgumentCaptor<Basket> basketCaptor = ArgumentCaptor.forClass(Basket.class);
        verify(basketsRepository).save(basketCaptor.capture());
        Basket saved = basketCaptor.getValue();
        assertThat(saved.getName()).isEqualTo("Updated Name");
        assertThat(saved.getComment()).isEqualTo("Updated comment");
        assertThat(saved.isShowPrices()).isTrue();
    }

    @Test
    @DisplayName("addOfferItemFromPriceList appends a BasketItem built from the matching pricelist entry")
    void addOfferItemFromPriceListAppendsBasketItemConstructedFromPricelistEntry() {
        // given
        Basket basket = basketBase();
        AvailabilityAndPrice entry = new AvailabilityAndPrice(
                "pim-1", "EAN-1", "MFN-1", "Brand", "GroupLabel", "Test Product",
                ProductCategory.Laptops, 199L, 1L, 3, 0L);
        Pricelist pricelist = new Pricelist("pl-1", List.of(entry));
        when(pricelistRepository.find(STORE_ID, "cat-1", "pl-1")).thenReturn(pricelist);
        when(basketsRepository.findById(STORE_ID, OFFER_ID)).thenReturn(Optional.of(basket));

        // when
        offerController.addOfferItemFromPriceList(OFFER_ID, "cat-1", "pl-1",
                ProductCategory.Laptops.name(), "GroupLabel", "Test Product");

        // then
        ArgumentCaptor<Basket> basketCaptor = ArgumentCaptor.forClass(Basket.class);
        verify(basketsRepository).save(basketCaptor.capture());
        assertThat(basketCaptor.getValue().getBasketItems())
                .extracting(BasketItem::getMfn).contains("MFN-1");
    }

    @Test
    @DisplayName("addOfferItemFromInventory appends a BasketItem built from matched inventory entry")
    void addOfferItemFromInventoryAppendsBasketItemFromMatchedInventory() {
        // given
        Basket basket = basketBase();
        MatchedInventory matchedInventory = MatchedInventory.empty(
                new pl.commercelink.inventory.InventoryKey("EAN-X", "MFN-X"));
        when(inventory.withEnabledSuppliersOnly(STORE_ID)).thenReturn(inventoryView);
        when(inventoryView.findByInventoryKey(any())).thenReturn(matchedInventory);
        when(basketsRepository.findById(STORE_ID, OFFER_ID)).thenReturn(Optional.of(basket));

        // when
        offerController.addOfferItemFromInventory(OFFER_ID, "EAN-X", "MFN-X");

        // then
        ArgumentCaptor<Basket> basketCaptor = ArgumentCaptor.forClass(Basket.class);
        verify(basketsRepository).save(basketCaptor.capture());
        assertThat(basketCaptor.getValue().getBasketItems()).hasSize(1);
    }

    @Test
    @DisplayName("addOfferItemFromPriceList assigns next position after existing basket items")
    void addOfferItemFromPriceListAssignsNextPositionAfterExistingItems() {
        // given
        Basket basket = basketBase();
        BasketItem existing = basketItem("MFN-A");
        existing.setPosition(0);
        basket.setBasketItems(List.of(existing));
        AvailabilityAndPrice entry = new AvailabilityAndPrice(
                "pim-1", "EAN-1", "MFN-1", "Brand", "GroupLabel", "Test Product",
                ProductCategory.Laptops, 199L, 1L, 3, 0L);
        Pricelist pricelist = new Pricelist("pl-1", List.of(entry));
        when(pricelistRepository.find("cat-1", "pl-1")).thenReturn(pricelist);
        when(basketsRepository.findById(STORE_ID, OFFER_ID)).thenReturn(Optional.of(basket));

        // when
        offerController.addOfferItemFromPriceList(OFFER_ID, "cat-1", "pl-1",
                ProductCategory.Laptops.name(), "GroupLabel", "Test Product");

        // then
        ArgumentCaptor<Basket> basketCaptor = ArgumentCaptor.forClass(Basket.class);
        verify(basketsRepository).save(basketCaptor.capture());
        List<BasketItem> savedItems = basketCaptor.getValue().getBasketItems();
        assertThat(savedItems).hasSize(2);
        assertThat(savedItems.get(1).getPosition()).isEqualTo(1);
    }

    @Test
    @DisplayName("removeOfferItem removes the basket item at the specified valid index")
    void removeOfferItemRemovesBasketItemAtSpecifiedIndexWhenInRange() {
        // given
        Basket basket = basketBase();
        BasketItem item0 = basketItem("MFN-A");
        BasketItem item1 = basketItem("MFN-B");
        basket.setBasketItems(List.of(item0, item1));
        when(basketsRepository.findById(STORE_ID, OFFER_ID)).thenReturn(Optional.of(basket));
        Model model = new ConcurrentModel();

        // when
        String view = offerController.removeOfferItem(OFFER_ID, 0, model);

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/offer/" + OFFER_ID);
        ArgumentCaptor<Basket> basketCaptor = ArgumentCaptor.forClass(Basket.class);
        verify(basketsRepository).save(basketCaptor.capture());
        assertThat(basketCaptor.getValue().getBasketItems())
                .extracting(BasketItem::getMfn).containsExactly("MFN-B");
    }

    private Basket basketBase() {
        Basket basket = new Basket();
        basket.setStoreId(STORE_ID);
        basket.setBasketId(OFFER_ID);
        basket.setBasketItems(Collections.emptyList());
        return basket;
    }

    private BasketItem basketItem(String mfn) {
        return new BasketItem("pim", "name", mfn,
                ProductCategory.Laptops, 100.0, 0, 1, null, 3, false);
    }
}
