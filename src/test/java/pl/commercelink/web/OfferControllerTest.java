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
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import pl.commercelink.baskets.Basket;
import pl.commercelink.baskets.BasketItem;
import pl.commercelink.baskets.BasketType;
import pl.commercelink.baskets.BasketsRepository;
import pl.commercelink.baskets.ContactDetails;
import pl.commercelink.baskets.OfferItemReloader;
import pl.commercelink.web.dtos.AddItemsForm;
import pl.commercelink.web.dtos.OfferCreationDto;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.invoicing.InvoicingService;
import pl.commercelink.offer.imports.OfferImporter;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.pricelist.AvailabilityAndPrice;
import pl.commercelink.pricelist.PricelistFinder;
import pl.commercelink.orders.PositionGroup;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.products.StoreCategories;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
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
    private StoreCategories storeCategories;
    @Mock
    private PricelistFinder pricelistFinder;
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
    private OfferImporter offerImporter;
    @Mock
    private MessageSource messageSource;
    @Mock
    private MultipartProperties multipartProperties;

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
    @DisplayName("saveAsTemplate keeps the service flag posted from the offer edit form")
    void saveAsTemplateKeepsPostedServiceFlag() {
        // given
        BasketItem serviceItem = basketItem("MFN-S", "");
        serviceItem.setService(true);
        Basket payload = new Basket();
        payload.setName("Offer");
        payload.setBasketItems(List.of(serviceItem));
        when(storesRepository.findById(STORE_ID)).thenReturn(new pl.commercelink.stores.Store());

        // when
        offerController.saveAsTemplate(OFFER_ID, payload, new ConcurrentModel());

        // then
        ArgumentCaptor<Basket> basketCaptor = ArgumentCaptor.forClass(Basket.class);
        verify(basketsRepository).save(basketCaptor.capture());
        List<BasketItem> savedItems = basketCaptor.getValue().getBasketItems();
        assertThat(savedItems).allMatch(BasketItem::isService);
        assertThat(savedItems).allMatch(i -> i.getCategory() == null);
    }

    @Test
    @DisplayName("createOfferFromImport keeps the service flag as resolved by the importer")
    void createOfferFromImportKeepsServiceFlagFromImporter() throws Exception {
        // given
        OfferCreationDto dto = new OfferCreationDto();
        dto.setType("CSV");
        ContactDetails contact = new ContactDetails();
        contact.setName("Jan");
        contact.setEmail("jan@test.local");
        dto.setContactDetails(contact);
        BasketItem importedServiceItem = basketItem("MFN-S", "Usługi dodatkowe");
        importedServiceItem.setService(true);
        when(offerImporters.stream()).thenReturn(Stream.of(offerImporter));
        when(offerImporter.getType()).thenReturn("CSV");
        when(offerImporter.importOffer(dto)).thenReturn(new java.util.ArrayList<>(List.of(importedServiceItem)));
        when(storesRepository.findById(STORE_ID)).thenReturn(new pl.commercelink.stores.Store());

        // when
        offerController.createOfferFromImport(dto, new RedirectAttributesModelMap(), Locale.ENGLISH);

        // then
        ArgumentCaptor<Basket> basketCaptor = ArgumentCaptor.forClass(Basket.class);
        verify(basketsRepository).save(basketCaptor.capture());
        assertThat(basketCaptor.getValue().getBasketItems()).allMatch(BasketItem::isService);
    }

    @Test
    @DisplayName("addOfferItems appends a BasketItem built from the matching pricelist entry")
    void addOfferItemsAppendsBasketItemConstructedFromPricelistEntry() {
        // given
        Basket basket = basketBase();
        AvailabilityAndPrice entry = new AvailabilityAndPrice(
                "pim-1", "EAN-1", "MFN-1", "Brand", "GroupLabel", "Test Product",
                "Laptops", 199L, 1L, 3, 0L, false);
        when(pricelistFinder.findByPimId(STORE_ID, "cat-1", entry.getPimId())).thenReturn(Optional.of(entry));
        when(basketsRepository.findById(STORE_ID, OFFER_ID)).thenReturn(Optional.of(basket));

        // when
        offerController.addOfferItems(OFFER_ID, form(AddItemsForm.Entry.fromPricelist("cat-1", "pim-1", 1)), null);

        // then
        ArgumentCaptor<Basket> basketCaptor = ArgumentCaptor.forClass(Basket.class);
        verify(basketsRepository).save(basketCaptor.capture());
        assertThat(basketCaptor.getValue().getBasketItems())
                .extracting(BasketItem::getMfn).contains("MFN-1");
    }

    @Test
    @DisplayName("addOfferItems appends a BasketItem built from matched inventory entry")
    void addOfferItemsAppendsBasketItemFromMatchedInventory() {
        // given
        Basket basket = basketBase();
        MatchedInventory matchedInventory = MatchedInventory.empty(
                new pl.commercelink.inventory.InventoryKey("EAN-X", "MFN-X"));
        when(inventory.withEnabledSuppliersOnly(STORE_ID)).thenReturn(inventoryView);
        when(inventoryView.findByInventoryKey(any())).thenReturn(matchedInventory);
        when(basketsRepository.findById(STORE_ID, OFFER_ID)).thenReturn(Optional.of(basket));

        // when
        offerController.addOfferItems(OFFER_ID, form(AddItemsForm.Entry.fromInventory("EAN-X", "MFN-X", 1)), null);

        // then
        ArgumentCaptor<Basket> basketCaptor = ArgumentCaptor.forClass(Basket.class);
        verify(basketsRepository).save(basketCaptor.capture());
        assertThat(basketCaptor.getValue().getBasketItems()).hasSize(1);
    }

    @Test
    @DisplayName("addOfferItems assigns next position after existing basket items")
    void addOfferItemsAssignsNextPositionAfterExistingItems() {
        // given
        Basket basket = basketBase();
        BasketItem existing = basketItem("MFN-A");
        existing.setPosition(0);
        basket.setBasketItems(List.of(existing));
        AvailabilityAndPrice entry = new AvailabilityAndPrice(
                "pim-1", "EAN-1", "MFN-1", "Brand", "GroupLabel", "Test Product",
                "Laptops", 199L, 1L, 3, 0L, false);
        when(pricelistFinder.findByPimId(STORE_ID, "cat-1", entry.getPimId())).thenReturn(Optional.of(entry));
        when(basketsRepository.findById(STORE_ID, OFFER_ID)).thenReturn(Optional.of(basket));

        // when
        offerController.addOfferItems(OFFER_ID, form(AddItemsForm.Entry.fromPricelist("cat-1", "pim-1", 1)), null);

        // then
        ArgumentCaptor<Basket> basketCaptor = ArgumentCaptor.forClass(Basket.class);
        verify(basketsRepository).save(basketCaptor.capture());
        List<BasketItem> savedItems = basketCaptor.getValue().getBasketItems();
        assertThat(savedItems).hasSize(2);
        assertThat(savedItems.get(1).getPosition()).isEqualTo(1);
    }

    @Test
    @DisplayName("addOfferItems inserts the product according to the catalog category order and survives reload")
    void addOfferItemsInsertsProductAccordingToCatalogCategoryOrder() {
        // given
        Basket basket = basketBase();
        BasketItem psu = basketItem("MFN-PSU", "PSU");
        basket.setBasketItems(List.of(psu));
        AvailabilityAndPrice entry = new AvailabilityAndPrice(
                "pim-1", "EAN-1", "MFN-CASE", "Brand", "GroupLabel", "Test Case",
                "Case", 199L, 1L, 3, 0L, false);
        when(pricelistFinder.findByPimId(STORE_ID, "cat-1", entry.getPimId())).thenReturn(Optional.of(entry));
        when(basketsRepository.findById(STORE_ID, OFFER_ID)).thenReturn(Optional.of(basket));
        when(productCatalogRepository.findById(STORE_ID, "cat-1")).thenReturn(
                catalog(categoryDefinition("Case", 1), categoryDefinition("PSU", 5)));

        // when
        offerController.addOfferItems(OFFER_ID, form(AddItemsForm.Entry.fromPricelist("cat-1", "pim-1", 1)), null);

        // then
        ArgumentCaptor<Basket> basketCaptor = ArgumentCaptor.forClass(Basket.class);
        verify(basketsRepository).save(basketCaptor.capture());
        Basket saved = basketCaptor.getValue();
        assertThat(saved.getBasketItems()).extracting(BasketItem::getMfn)
                .containsExactly("MFN-CASE", "MFN-PSU");
        assertThat(saved.getBasketItems()).extracting(BasketItem::getPosition).containsExactly(0, 1);

        // then the order survives a persistence round-trip (setter reindexes positions on load)
        saved.setBasketItems(saved.getBasketItems());
        assertThat(saved.getBasketItems()).extracting(BasketItem::getMfn)
                .containsExactly("MFN-CASE", "MFN-PSU");
        assertThat(saved.getBasketItems()).extracting(BasketItem::getPosition).containsExactly(0, 1);
    }

    @Test
    @DisplayName("addOfferItems appends after existing products of the same catalog category")
    void addOfferItemsAppendsAfterExistingProductsOfSameCategory() {
        // given
        Basket basket = basketBase();
        BasketItem firstCase = basketItem("MFN-CASE-1", "Case");
        BasketItem psu = basketItem("MFN-PSU", "PSU");
        basket.setBasketItems(List.of(firstCase, psu));
        AvailabilityAndPrice entry = new AvailabilityAndPrice(
                "pim-2", "EAN-2", "MFN-CASE-2", "Brand", "GroupLabel", "Second Case",
                "Case", 149L, 1L, 3, 0L, false);
        when(pricelistFinder.findByPimId(STORE_ID, "cat-1", entry.getPimId())).thenReturn(Optional.of(entry));
        when(basketsRepository.findById(STORE_ID, OFFER_ID)).thenReturn(Optional.of(basket));
        when(productCatalogRepository.findById(STORE_ID, "cat-1")).thenReturn(
                catalog(categoryDefinition("Case", 1), categoryDefinition("PSU", 5)));

        // when
        offerController.addOfferItems(OFFER_ID, form(AddItemsForm.Entry.fromPricelist("cat-1", "pim-2", 1)), null);

        // then
        ArgumentCaptor<Basket> basketCaptor = ArgumentCaptor.forClass(Basket.class);
        verify(basketsRepository).save(basketCaptor.capture());
        assertThat(basketCaptor.getValue().getBasketItems()).extracting(BasketItem::getMfn)
                .containsExactly("MFN-CASE-1", "MFN-CASE-2", "MFN-PSU");
    }

    @Test
    @DisplayName("addOfferItems marks a row carrying the service flag as a service")
    void addOfferItemsMarksRowCarryingServiceFlagAsService() {
        // given
        Basket basket = basketBase();
        AvailabilityAndPrice entry = new AvailabilityAndPrice(
                "pim-1", "EAN-1", "MFN-S", "", "Montaż", "Montaż komputera",
                "Usługi dodatkowe", 250L, 1L, 1, 0L, true);
        when(pricelistFinder.findByPimId(STORE_ID, "cat-1", entry.getPimId())).thenReturn(Optional.of(entry));
        when(basketsRepository.findById(STORE_ID, OFFER_ID)).thenReturn(Optional.of(basket));

        // when
        offerController.addOfferItems(OFFER_ID, form(AddItemsForm.Entry.fromPricelist("cat-1", "pim-1", 1)), null);

        // then
        ArgumentCaptor<Basket> basketCaptor = ArgumentCaptor.forClass(Basket.class);
        verify(basketsRepository).save(basketCaptor.capture());
        BasketItem savedItem = basketCaptor.getValue().getBasketItems().get(0);
        assertThat(savedItem.isService()).isTrue();
        assertThat(savedItem.getPosition()).isEqualTo(PositionGroup.SERVICE_GROUP_START);
    }

    @Test
    @DisplayName("addOfferItems keeps services in the service position band despite catalog sequence number")
    void addOfferItemsKeepsServicesInServiceBand() {
        // given
        Basket basket = basketBase();
        AvailabilityAndPrice entry = new AvailabilityAndPrice(
                "pim-1", "EAN-1", "MFN-1", "Brand", "GroupLabel", "Montaz",
                "Usługi dodatkowe", 49L, 1L, 3, 0L, true);
        when(pricelistFinder.findByPimId(STORE_ID, "cat-1", entry.getPimId())).thenReturn(Optional.of(entry));
        when(basketsRepository.findById(STORE_ID, OFFER_ID)).thenReturn(Optional.of(basket));
        when(productCatalogRepository.findById(STORE_ID, "cat-1")).thenReturn(
                catalog(categoryDefinition("Usługi dodatkowe", 3)));

        // when
        offerController.addOfferItems(OFFER_ID, form(AddItemsForm.Entry.fromPricelist("cat-1", "pim-1", 1)), null);

        // then
        ArgumentCaptor<Basket> basketCaptor = ArgumentCaptor.forClass(Basket.class);
        verify(basketsRepository).save(basketCaptor.capture());
        assertThat(basketCaptor.getValue().getBasketItems().get(0).getPosition())
                .isEqualTo(PositionGroup.SERVICE_GROUP_START);
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

        // when
        String view = offerController.removeOfferItem(OFFER_ID, 0, null);

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/offer/" + OFFER_ID);
        ArgumentCaptor<Basket> basketCaptor = ArgumentCaptor.forClass(Basket.class);
        verify(basketsRepository).save(basketCaptor.capture());
        assertThat(basketCaptor.getValue().getBasketItems())
                .extracting(BasketItem::getMfn).containsExactly("MFN-B");
    }

    @Test
    @DisplayName("addOfferItems adds pricelist and inventory entries of one batch in the posted sequence and saves the offer once")
    void addOfferItemsAddsPricelistAndInventoryEntriesInOneSave() {
        // given
        Basket basket = basketBase();
        AvailabilityAndPrice laptop = new AvailabilityAndPrice(
                "pim-1", "EAN-1", "MFN-1", "Brand", "GroupLabel", "Laptop",
                "Laptops", 199L, 1L, 3, 0L, false);
        AvailabilityAndPrice mouse = new AvailabilityAndPrice(
                "pim-2", "EAN-2", "MFN-2", "Brand", "GroupLabel", "Mouse",
                "Mice", 49L, 1L, 3, 0L, false);
        when(pricelistFinder.findByPimId(STORE_ID, "cat-1", "pim-1")).thenReturn(Optional.of(laptop));
        when(pricelistFinder.findByPimId(STORE_ID, "cat-1", "pim-2")).thenReturn(Optional.of(mouse));
        when(inventory.withEnabledSuppliersOnly(STORE_ID)).thenReturn(inventoryView);
        when(inventoryView.findByInventoryKey(any())).thenReturn(MatchedInventory.empty(
                new pl.commercelink.inventory.InventoryKey("EAN-X", "MFN-X")));
        when(basketsRepository.findById(STORE_ID, OFFER_ID)).thenReturn(Optional.of(basket));

        // when
        offerController.addOfferItems(OFFER_ID, form(
                AddItemsForm.Entry.fromPricelist("cat-1", "pim-1", 2),
                AddItemsForm.Entry.fromInventory("EAN-X", "MFN-X", 1),
                AddItemsForm.Entry.fromPricelist("cat-1", "pim-2", 1)), null);

        // then
        ArgumentCaptor<Basket> basketCaptor = ArgumentCaptor.forClass(Basket.class);
        verify(basketsRepository).save(basketCaptor.capture());
        List<BasketItem> savedItems = basketCaptor.getValue().getBasketItems();
        assertThat(savedItems).extracting(BasketItem::getMfn).containsExactly("MFN-1", "MFN-X", "MFN-2");
        assertThat(savedItems).extracting(BasketItem::getQty).containsExactly(2L, 1L, 1L);
    }

    @Test
    @DisplayName("addOfferItems rejects the whole batch when one pricelist entry is unknown")
    void addOfferItemsRejectsWholeBatchWhenPricelistEntryUnknown() {
        // given
        AvailabilityAndPrice laptop = new AvailabilityAndPrice(
                "pim-1", "EAN-1", "MFN-1", "Brand", "GroupLabel", "Laptop",
                "Laptops", 199L, 1L, 3, 0L, false);
        when(pricelistFinder.findByPimId(STORE_ID, "cat-1", "pim-1")).thenReturn(Optional.of(laptop));
        when(pricelistFinder.findByPimId(STORE_ID, "cat-1", "pim-missing")).thenReturn(Optional.empty());
        when(basketsRepository.findById(STORE_ID, OFFER_ID)).thenReturn(Optional.of(basketBase()));
        AddItemsForm form = form(
                AddItemsForm.Entry.fromPricelist("cat-1", "pim-1", 1),
                AddItemsForm.Entry.fromPricelist("cat-1", "pim-missing", 1));

        // when / then
        assertThatThrownBy(() -> offerController.addOfferItems(OFFER_ID, form, null))
                .isInstanceOf(ResponseStatusException.class);
        verify(basketsRepository, never()).save(any());
    }

    private AddItemsForm form(AddItemsForm.Entry... entries) {
        AddItemsForm form = new AddItemsForm();
        form.setItems(List.of(entries));
        return form;
    }

    @Test
    void newOfferPageAnswersNotFoundForAnUnknownIntent() {
        // when / then
        assertThatThrownBy(() -> offerController.showContactDetailsForm("xyz", null, new ExtendedModelMap(), Locale.ENGLISH))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> offerController.createOfferWithContact(filledContact(), "xyz", null, null, "Offer",
                new RedirectAttributesModelMap(), Locale.ENGLISH))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(basketsRepository, never()).save(any());
    }

    @Test
    void newOfferPageAnswersNotFoundForASourceThatIsMissingOfAnotherStoreOrOfTheWrongType() {
        // given
        Basket offer = sourceBasket("offer-2", BasketType.Offer);
        when(basketsRepository.findById(STORE_ID, "offer-2")).thenReturn(Optional.of(offer));
        when(basketsRepository.findById(STORE_ID, "other-store-basket")).thenReturn(Optional.empty());

        // when / then
        for (String[] request : new String[][]{{"copy", null}, {"copy", ""}, {"copy", "other-store-basket"},
                {"template", "offer-2"}, {"template", null}, {"copy", "x".repeat(300)}}) {
            assertThatThrownBy(() -> offerController.showContactDetailsForm(request[0], request[1], new ExtendedModelMap(), Locale.ENGLISH))
                    .as(String.join(" ", request[0], String.valueOf(request[1]).substring(0, Math.min(20, String.valueOf(request[1]).length()))))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
            assertThatThrownBy(() -> offerController.createOfferWithContact(filledContact(), request[0], request[1], null, "Offer",
                    new RedirectAttributesModelMap(), Locale.ENGLISH))
                    .isInstanceOf(ResponseStatusException.class);
        }
        verify(basketsRepository, never()).save(any());
    }

    @Test
    void newOfferPageTellsWhereTheOfferStartsFromAndFillsTheContactFromAShopBasket() {
        // given
        Basket shopBasket = sourceBasket("4f0c2a91-0000-0000-0000-000000000000", BasketType.Basket);
        BillingDetails billing = new BillingDetails();
        billing.setName(" Ewa ");
        billing.setEmail("ewa@example.com");
        shopBasket.setBillingDetails(billing);
        when(basketsRepository.findById(STORE_ID, shopBasket.getBasketId())).thenReturn(Optional.of(shopBasket));
        when(basketsRepository.findById(STORE_ID, "offer-2")).thenReturn(Optional.of(sourceBasket("offer-2", BasketType.Offer)));
        when(basketsRepository.findById(STORE_ID, "template-1")).thenReturn(Optional.of(sourceBasket("template-1", BasketType.OfferTemplate)));
        when(messageSource.getMessage(any(String.class), any(), any(Locale.class))).thenReturn("Basket 4f0c2a91");
        Model fromBasket = new ExtendedModelMap();
        Model fromOffer = new ExtendedModelMap();
        Model fromTemplate = new ExtendedModelMap();
        Model copyOfTemplate = new ExtendedModelMap();
        Model empty = new ExtendedModelMap();

        // when
        offerController.showContactDetailsForm("copy", shopBasket.getBasketId(), fromBasket, Locale.ENGLISH);
        offerController.showContactDetailsForm("copy", "offer-2", fromOffer, Locale.ENGLISH);
        offerController.showContactDetailsForm("template", "template-1", fromTemplate, Locale.ENGLISH);
        offerController.showContactDetailsForm("copy", "template-1", copyOfTemplate, Locale.ENGLISH);
        offerController.showContactDetailsForm("manual", null, empty, Locale.ENGLISH);

        // then
        assertThat(fromBasket.getAttribute("source")).isEqualTo("basket");
        assertThat(fromBasket.getAttribute("offerName")).isEqualTo("Basket 4f0c2a91");
        ContactDetails contact = (ContactDetails) fromBasket.getAttribute("contactDetails");
        assertThat(contact.getName()).isEqualTo("Ewa");
        assertThat(contact.getEmail()).isEqualTo("ewa@example.com");
        assertThat(fromOffer.getAttribute("source")).isEqualTo("offer");
        assertThat(fromOffer.getAttribute("offerName")).isNull();
        assertThat(fromTemplate.getAttribute("source")).isEqualTo("template");
        assertThat(copyOfTemplate.getAttribute("source")).isEqualTo("template");
        assertThat(empty.getAttribute("source")).isNull();
    }

    @Test
    void refusedContactGoesBackToTheSamePageWithTheSourceEncoded() {
        // given
        when(basketsRepository.findById(STORE_ID, "a b&c")).thenReturn(Optional.of(sourceBasket("a b&c", BasketType.Offer)));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String view = offerController.createOfferWithContact(new ContactDetails(), "copy", "a b&c", null, "Offer",
                redirect, Locale.ENGLISH);

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/offer/new?intent=copy&sourceId=a%20b%26c");
        assertThat(redirect.getFlashAttributes()).containsKey("errorMessage");
        verify(basketsRepository, never()).save(any());
    }

    @Test
    void offerFromATemplateCopiesItsItemsWithTheContactAndNameFromTheForm() {
        // given
        Basket template = sourceBasket("template-1", BasketType.OfferTemplate);
        template.setBasketItems(new java.util.ArrayList<>(List.of(basketItem("MFN-1"), basketItem("MFN-2"))));
        when(basketsRepository.findById(STORE_ID, "template-1")).thenReturn(Optional.of(template));
        when(storesRepository.findById(STORE_ID)).thenReturn(new Store());

        // when
        String view = offerController.createOfferWithContact(filledContact(), "template", "template-1", "gclid-1", "New offer",
                new RedirectAttributesModelMap(), Locale.ENGLISH);

        // then
        ArgumentCaptor<Basket> basketCaptor = ArgumentCaptor.forClass(Basket.class);
        verify(basketsRepository).save(basketCaptor.capture());
        Basket saved = basketCaptor.getValue();
        assertThat(view).isEqualTo("redirect:/dashboard/offer/" + saved.getBasketId());
        assertThat(saved.getType()).isEqualTo(BasketType.Offer);
        assertThat(saved.getBasketItems()).extracting(BasketItem::getMfn).containsExactly("MFN-1", "MFN-2");
        assertThat(saved.getName()).isEqualTo("New offer");
        assertThat(saved.getGclid()).isEqualTo("gclid-1");
        assertThat(saved.getContactDetails().getName()).isEqualTo("Jan");
    }

    private Basket sourceBasket(String id, BasketType type) {
        Basket basket = basketBase();
        basket.setBasketId(id);
        basket.setType(type);
        return basket;
    }

    private static ContactDetails filledContact() {
        ContactDetails contact = new ContactDetails();
        contact.setName("Jan");
        contact.setEmail("jan@test.local");
        return contact;
    }

    private Basket basketBase() {
        Basket basket = new Basket();
        basket.setStoreId(STORE_ID);
        basket.setBasketId(OFFER_ID);
        basket.setBasketItems(Collections.emptyList());
        return basket;
    }

    private BasketItem basketItem(String mfn) {
        return basketItem(mfn, "Laptops");
    }

    private BasketItem basketItem(String mfn, String category) {
        return new BasketItem("pim", "name", mfn,
                category, 100.0, 0, 1, null, 3, false);
    }

    private CategoryDefinition categoryDefinition(String category, int sequenceNumber) {
        CategoryDefinition definition = new CategoryDefinition();
        definition.setCategoryId("cat-def-" + category);
        definition.setName(category);
        definition.setCategory(category);
        definition.setSequenceNumber(sequenceNumber);
        return definition;
    }

    private ProductCatalog catalog(CategoryDefinition... definitions) {
        ProductCatalog catalog = new ProductCatalog(STORE_ID, "catalog");
        catalog.setCategories(List.of(definitions));
        return catalog;
    }

    @Test
    void removingAnItemComesBackToThePageWithTheListItWasOpenedFrom() {
        // given
        Basket basket = basketBase();
        basket.setBasketItems(List.of(basketItem("MFN-A"), basketItem("MFN-B")));
        when(basketsRepository.findById(STORE_ID, OFFER_ID)).thenReturn(Optional.of(basket));

        // when
        String view = offerController.removeOfferItem(OFFER_ID, 0, "/dashboard/offers?segment=expired");

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/offer/" + OFFER_ID + "?returnTo=%2Fdashboard%2Foffers%3Fsegment%3Dexpired");
    }

    @Test
    void actionsOnAnUnknownOfferAreNotFound() {
        // given
        when(basketsRepository.findById(STORE_ID, "missing")).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> offerController.removeOfferItem("missing", 0, null))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> offerController.recalculateOffer("missing", null, new RedirectAttributesModelMap(), Locale.ENGLISH))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> offerController.duplicateOffer("missing", true))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void anOfferPageAddressKeepsOnlyAListAddress() {
        // when / then
        assertThat(OfferController.offerPageHref("o-1", "/dashboard/offers?segment=templates"))
                .isEqualTo("/dashboard/offer/o-1?returnTo=%2Fdashboard%2Foffers%3Fsegment%3Dtemplates");
        assertThat(OfferController.offerPageHref("o-1", "//evil.example")).isEqualTo("/dashboard/offer/o-1");
        assertThat(OfferController.offerPageHref("o-1", null)).isEqualTo("/dashboard/offer/o-1");
        assertThat(OfferController.offerHref("o-1", "/recalculate", "/dashboard/offers"))
                .isEqualTo("/dashboard/offer/o-1/recalculate?returnTo=%2Fdashboard%2Foffers");
    }
}
