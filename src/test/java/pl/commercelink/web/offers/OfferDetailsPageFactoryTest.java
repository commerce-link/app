package pl.commercelink.web.offers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.support.ResourceBundleMessageSource;
import pl.commercelink.baskets.Basket;
import pl.commercelink.baskets.BasketItem;
import pl.commercelink.baskets.BasketType;
import pl.commercelink.baskets.ContactDetails;
import pl.commercelink.baskets.OfferItem;
import pl.commercelink.baskets.OfferItemReloader;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.products.StoreCategories;
import pl.commercelink.stores.CheckoutConfiguration;
import pl.commercelink.stores.DeliveryOption;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.offers.OfferDetailsPage.GroupHead;
import pl.commercelink.web.offers.OfferDetailsPage.Item;
import pl.commercelink.web.orders.ItemMargin;
import pl.commercelink.web.orders.Money;

import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OfferDetailsPageFactoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 19, 10, 0);
    private static final Locale PL = Locale.forLanguageTag("pl");

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private OfferItemReloader offerItemReloader;
    @Mock
    private ProductCatalogRepository productCatalogRepository;
    @Mock
    private StoreCategories storeCategories;

    private OfferDetailsPageFactory factory;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        factory = new OfferDetailsPageFactory(storesRepository, offerItemReloader, productCatalogRepository,
                storeCategories, source, "https://app.example");

        Store store = new Store();
        CheckoutConfiguration checkout = new CheckoutConfiguration();
        DeliveryOption inpost = new DeliveryOption();
        inpost.setId("inpost");
        inpost.setName("Kurier InPost");
        inpost.setPrice(19.99);
        checkout.setDeliveryOptions(new LinkedList<>(List.of(inpost)));
        store.setCheckoutConfiguration(checkout);
        when(storesRepository.findById("store-1")).thenReturn(store);
        when(offerItemReloader.reload(any())).thenReturn(List.of());
        when(offerItemReloader.recalculate(any())).thenReturn(List.of());
    }

    private static Basket basket(BasketItem... items) {
        Basket basket = new Basket();
        basket.setStoreId("store-1");
        basket.setBasketId("8f3a21c7-1b2d-4e5f-9a0b-1c2d3e4f5a6b");
        basket.setType(BasketType.Offer);
        basket.setCreatedAt(NOW.minusDays(1));
        basket.setBasketItems(new LinkedList<>(List.of(items)));
        return basket;
    }

    private static BasketItem item(String name, long qty, double price, double cost) {
        BasketItem item = new BasketItem();
        item.setName(name);
        item.setMfn("MFN-" + name);
        item.setCategory("laptops");
        item.setQty(qty);
        item.setUnitPrice(price);
        item.setUnitCost(cost);
        return item;
    }

    private OfferDetailsPage build(Basket basket) {
        return factory.build(basket, null, PL, NOW);
    }

    @Test
    void summaryUsesTodaysFormulas() {
        // given
        Basket basket = basket(item("A", 3, 6899, 5810), item("B", 3, 799, 0));
        basket.setDeliveryOptionId("inpost");

        // when
        OfferDetailsPage.Summary summary = build(basket).summary();

        // then
        assertThat(summary.total()).isEqualTo(Money.format(23094 + 19.99));
        assertThat(summary.cost()).isEqualTo(Money.format(17430));
        assertThat(summary.profitGross()).isEqualTo(Money.format(23113.99 - 17430));
        assertThat(summary.profitNet()).isEqualTo(Money.format(Math.round((23113.99 - 17430) / 1.23 * 100.0) / 100.0));
        assertThat(summary.deliveryName()).isEqualTo("Kurier InPost");
        assertThat(summary.deliveryPriceValue()).isEqualTo(19.99);
        assertThat(summary.deliveryRemoved()).isFalse();
    }

    private OfferItem offerFor(BasketItem item, double median) {
        OfferItem offer = mock(OfferItem.class);
        when(offer.getBasketItem()).thenReturn(item);
        when(offer.isExistInInventory()).thenReturn(true);
        when(offer.getLowestCost()).thenReturn(median - 100);
        when(offer.getMedianCost()).thenReturn(median);
        when(offer.getSupplierCount()).thenReturn(3);
        return offer;
    }

    private OfferDetailsPage buildWithMarket(Basket basket, OfferItem... offers) {
        when(offerItemReloader.reload(any())).thenReturn(List.of(offers));
        return build(basket);
    }

    @Test
    void anItemWithoutCostTakesTheSuppliersMedianForItsMargin() {
        // given
        BasketItem noCost = item("A", 1, 2199, 0);
        Basket basket = basket(noCost);

        // when
        Item line = (Item) buildWithMarket(basket, offerFor(noCost, 1721.40)).lines().get(0);

        // then
        assertThat(line.costEstimated()).isTrue();
        assertThat(line.costForMargin()).isEqualTo(1721.40);
        assertThat(line.medianCost()).isEqualTo(1721.40);
        assertThat(line.unitCost()).isZero();
        assertThat(line.margin().percent()).isEqualTo(ItemMargin.of(2199, 1721.40, 1.23).percent());
        assertThat(line.margin().tone()).isEqualTo(ItemMargin.Tone.OK);
        assertThat(noCost.getUnitCost()).isZero();
    }

    @Test
    void anItemWithItsOwnCostIgnoresTheMedian() {
        // given
        BasketItem costed = item("A", 1, 2199, 1534.17);
        Basket basket = basket(costed);

        // when
        Item line = (Item) buildWithMarket(basket, offerFor(costed, 1721.40)).lines().get(0);

        // then
        assertThat(line.costEstimated()).isFalse();
        assertThat(line.costForMargin()).isEqualTo(1534.17);
        assertThat(line.medianCost()).isEqualTo(1721.40);
    }

    @Test
    void anItemWithoutSupplierOffersStaysUnknown() {
        // given
        Basket basket = basket(item("A", 1, 2199, 0));

        // when
        Item line = (Item) build(basket).lines().get(0);

        // then
        assertThat(line.costEstimated()).isFalse();
        assertThat(line.costForMargin()).isZero();
        assertThat(line.medianCost()).isZero();
        assertThat(line.margin().tone()).isEqualTo(ItemMargin.Tone.UNKNOWN);
    }

    @Test
    void aServiceNeverGetsAnEstimate() {
        // given
        BasketItem service = item("S", 1, 199, 0);
        service.setService(true);
        Basket basket = basket(service);

        // when
        Item line = (Item) buildWithMarket(basket, offerFor(service, 100)).lines().get(0);

        // then
        assertThat(line.margin()).isNull();
        assertThat(line.costEstimated()).isFalse();
        assertThat(line.medianCost()).isZero();
    }

    @Test
    void theSummaryCostCountsTheEstimateOfChosenItemsOnly() {
        // given
        BasketItem chosen = item("A", 1, 100, 50);
        chosen.setVariantGroupId("Monitor");
        chosen.setVariantSelected(true);
        BasketItem unpicked = item("B", 1, 100, 0);
        unpicked.setVariantGroupId("Monitor");
        BasketItem counted = item("C", 2, 300, 0);
        Basket basket = basket(chosen, unpicked, counted);

        // when
        OfferDetailsPage.Summary summary = buildWithMarket(basket, offerFor(unpicked, 70), offerFor(counted, 120)).summary();

        // then
        assertThat(summary.cost()).isEqualTo(Money.format(50 + 2 * 120));
        assertThat(summary.profitGross()).isEqualTo(Money.format(700 - 290));
        assertThat(summary.costEstimated()).isTrue();
    }

    @Test
    void theSummaryIsNotEstimatedWhenOnlyAnUnpickedVariantLacksCost() {
        // given
        BasketItem chosen = item("A", 1, 100, 50);
        chosen.setVariantGroupId("Monitor");
        chosen.setVariantSelected(true);
        BasketItem unpicked = item("B", 1, 100, 0);
        unpicked.setVariantGroupId("Monitor");
        Basket basket = basket(chosen, unpicked, item("C", 1, 10, 5));

        // when
        OfferDetailsPage.Summary summary = buildWithMarket(basket, offerFor(unpicked, 70)).summary();

        // then
        assertThat(summary.cost()).isEqualTo(Money.format(55));
        assertThat(summary.costEstimated()).isFalse();
    }

    @Test
    void templateIsRecalculatedOfferIsReloaded() {
        // given
        Basket template = basket(item("A", 1, 10, 5));
        template.setType(BasketType.OfferTemplate);
        Basket offer = basket(item("A", 1, 10, 5));

        // when
        build(template);
        build(offer);

        // then
        verify(offerItemReloader).recalculate(template);
        verify(offerItemReloader).reload(offer);
        verify(offerItemReloader, never()).recalculate(offer);
        verify(offerItemReloader, never()).reload(template);
    }

    @Test
    void aDraftOfATemplateKeepsThePostedPricesAndCarriesTheVersion() {
        // given
        Basket template = basket(item("A", 1, 10, 5));
        template.setType(BasketType.OfferTemplate);
        template.setVersion(3L);

        // when
        OfferDetailsPage page = factory.buildDraft(template, null, PL, NOW);

        // then
        verify(offerItemReloader).reload(template);
        verify(offerItemReloader, never()).recalculate(any());
        assertThat(page.version()).isEqualTo(3L);
    }

    @Test
    void servicesAreListedApartFromProducts() {
        // given
        BasketItem dell = item("Dell", 1, 1200, 900);
        dell.setVariantGroupId("Monitor");
        dell.setVariantSelected(true);
        BasketItem lg = item("LG", 1, 1100, 850);
        lg.setVariantGroupId("Monitor");
        BasketItem samsung = item("Samsung", 1, 1150, 870);
        samsung.setVariantGroupId("Monitor");
        BasketItem assembly = item("Montaz", 1, 300, 0);
        assembly.setService(true);
        Basket basket = basket(item("Laptop", 1, 6899, 5810), dell, lg, samsung, assembly, item("Mysz", 2, 99, 50),
                item("Kabel", 3, 19, 8));

        // when
        OfferDetailsPage page = build(basket);

        // then
        assertThat(page.lines()).hasSize(8);
        assertThat(page.productLines()).hasSize(7);
        assertThat(page.productLines()).filteredOn(Item.class::isInstance).hasSize(6);
        assertThat(page.productLines()).filteredOn(GroupHead.class::isInstance).containsExactly(new GroupHead("Monitor", 3));
        assertThat(page.serviceLines()).singleElement().isInstanceOf(Item.class)
                .extracting(line -> ((Item) line).name()).isEqualTo("Montaz");
        assertThat(((Item) page.serviceLines().get(0)).index()).isEqualTo(4);
    }

    @Test
    void aGroupMixingProductsAndServicesStaysWholeInTheSectionOfItsFirstItem() {
        // given
        BasketItem service = item("Gwarancja", 1, 200, 0);
        service.setService(true);
        service.setVariantGroupId("Opieka");
        BasketItem product = item("Gwarancja produkt", 1, 250, 100);
        product.setVariantGroupId("Opieka");
        Basket basket = basket(item("Laptop", 1, 6899, 5810), service, product);

        // when
        OfferDetailsPage page = build(basket);

        // then
        assertThat(page.productLines()).singleElement().extracting(line -> ((Item) line).name()).isEqualTo("Laptop");
        assertThat(page.serviceLines()).hasSize(3);
        assertThat(page.serviceLines().get(0)).isEqualTo(new GroupHead("Opieka", 2));
    }

    @Test
    void variantGroupProducesHeadThenItems() {
        // given
        BasketItem first = item("A", 1, 100, 50);
        first.setVariantGroupId("Monitor");
        first.setVariantSelected(true);
        BasketItem second = item("B", 1, 120, 60);
        second.setVariantGroupId("Monitor");
        Basket basket = basket(first, second, item("C", 1, 10, 5));

        // when
        OfferDetailsPage page = build(basket);

        // then
        assertThat(page.lines()).hasSize(4);
        assertThat(page.lines().get(0)).isEqualTo(new GroupHead("Monitor", 2));
        Item a = (Item) page.lines().get(1);
        Item b = (Item) page.lines().get(2);
        assertThat(a.variantSelected()).isTrue();
        assertThat(b.variantSelected()).isFalse();
        assertThat(page.lines().get(3)).isInstanceOf(Item.class);
        assertThat(page.summary().variantsNote()).isTrue();
    }

    @Test
    void zeroQuantityOrBlankNameIsFlaggedIncomplete() {
        // given
        BasketItem noQty = item("A", 0, 10, 5);
        BasketItem noName = item(" ", 1, 10, 5);
        BasketItem fine = item("C", 1, 10, 5);
        Basket basket = basket(noQty, noName, fine);

        // when
        List<OfferDetailsPage.Line> lines = build(basket).lines();

        // then
        assertThat(((Item) lines.get(0)).incomplete()).isEqualTo(!noQty.isComplete()).isTrue();
        assertThat(((Item) lines.get(1)).incomplete()).isEqualTo(!noName.isComplete()).isTrue();
        assertThat(((Item) lines.get(2)).incomplete()).isFalse();
    }

    @Test
    void productWithoutCategoryIsNotFlaggedBecauseTheTableKeepsIt() {
        // given
        BasketItem noCategory = item("A", 1, 10, 5);
        noCategory.setCategory(null);
        Basket basket = basket(noCategory);

        // when
        List<OfferDetailsPage.Line> lines = build(basket).lines();

        // then
        assertThat(((Item) lines.get(0)).incomplete()).isFalse();
    }

    @Test
    void removedDeliveryOptionDoesNotThrow() {
        // given
        Basket basket = basket(item("A", 1, 100, 50));
        basket.setDeliveryOptionId("gone");

        // when
        OfferDetailsPage.Summary summary = build(basket).summary();

        // then
        assertThat(summary.deliveryRemoved()).isTrue();
        assertThat(summary.deliveryPriceValue()).isZero();
        assertThat(summary.total()).isEqualTo(Money.format(100));
    }

    @Test
    void proformaNeedsBillingDetails() {
        // given
        Basket without = basket(item("A", 1, 100, 50));
        Basket with = basket(item("A", 1, 100, 50));
        with.setBillingDetails(new BillingDetails());

        // when / then
        assertThat(build(without).proformaAvailable()).isFalse();
        assertThat(build(with).proformaAvailable()).isTrue();
    }

    @Test
    void missingContactHidesCopyWithContact() {
        // given
        Basket empty = basket(item("A", 1, 100, 50));
        empty.setContactDetails(new ContactDetails());
        Basket filled = basket(item("A", 1, 100, 50));
        ContactDetails contact = new ContactDetails();
        contact.setName("Jan");
        contact.setEmail("jan@example.com");
        filled.setContactDetails(contact);

        // when
        OfferDetailsPage emptyPage = build(empty);
        OfferDetailsPage filledPage = build(filled);

        // then
        assertThat(emptyPage.copyWithContactAvailable()).isFalse();
        assertThat(emptyPage.customer().contactFilled()).isFalse();
        assertThat(filledPage.copyWithContactAvailable()).isTrue();
        assertThat(filledPage.customer().contactFilled()).isTrue();
    }

    @Test
    void backHrefFollowsSafeReturnTo() {
        // given
        Basket offer = basket(item("A", 1, 100, 50));
        Basket template = basket(item("A", 1, 100, 50));
        template.setType(BasketType.OfferTemplate);

        // when
        String kept = factory.build(offer, "/dashboard/offers?q=x", PL, NOW).backHref();
        String foreign = factory.build(offer, "https://evil", PL, NOW).backHref();
        OfferDetailsPage templatePage = factory.build(template, null, PL, NOW);

        // then
        assertThat(kept).isEqualTo("/dashboard/offers?q=x");
        assertThat(foreign).isEqualTo(OfferDetailsPageFactory.LIST_PATH);
        assertThat(templatePage.backHref()).isEqualTo(OfferDetailsPageFactory.TEMPLATES_PATH);
        assertThat(templatePage.backLabelKey()).isEqualTo("offers.details.back.templates");
    }

    @Test
    void validityPillMatchesList() {
        // given
        Basket basket = basket(item("A", 1, 100, 50));
        basket.setExpiresAt(NOW.plusDays(3));

        // when
        OfferDetailsPage.Pill pill = build(basket).validity();

        // then
        assertThat(pill.key()).isEqualTo("offers.list.pill.expiring");
        assertThat(pill.tone()).isEqualTo("is-warn");
        assertThat(build(basket).meta().dueNote()).contains("22.10");
    }

    @Test
    void termsShowTheDeadlineWithTheDayCountOfTheHeader() {
        // given
        Basket inFiveDays = basket(item("A", 1, 100, 50));
        inFiveDays.setExpiresAt(LocalDateTime.of(2026, 10, 24, 23, 59));
        Basket tomorrow = basket(item("A", 1, 100, 50));
        tomorrow.setExpiresAt(NOW.plusDays(1));
        Basket expired = basket(item("A", 1, 100, 50));
        expired.setExpiresAt(NOW.minusDays(2));

        // when
        OfferDetailsPage page = build(inFiveDays);

        // then
        assertThat(page.terms().expiresAtText()).isEqualTo("24.10.2026, 23:59 (5 dni)");
        assertThat(page.meta().dueNote()).isEqualTo("do 24.10 (5 dni)");
        assertThat(build(tomorrow).terms().expiresAtText()).isEqualTo("20.10.2026, 10:00 (jutro)");
        assertThat(build(expired).terms().expiresAtText()).isEqualTo("17.10.2026, 10:00 (minęła)");
        assertThat(build(basket(item("A", 1, 100, 50))).terms().expiresAtText()).isNull();
    }

    @Test
    void storeThatNeverSavedCheckoutSettingsShowsTheOffer() {
        // given: successor of OfferControllerTest.showsOfferDetailsForAStoreThatHasNeverSavedCheckoutSettings
        Store store = new Store();
        store.setStoreId("store-1");
        when(storesRepository.findById("store-1")).thenReturn(store);
        Basket offer = basket(item("A", 1, 100, 50));
        offer.setDeliveryOptionId("inpost");

        // when
        OfferDetailsPage page = build(offer);

        // then
        assertThat(store.getCheckoutConfiguration()).isNull();
        assertThat(page.terms().deliveryChoices()).isEmpty();
        assertThat(page.summary().deliveryName()).isNull();
        assertThat(page.summary().total()).isEqualTo(Money.format(100));
    }

    @Test
    void priceAndCostFieldsHoldWholeGroszeSoATableSaveStoresThemRounded() {
        // given: the user's decision K1 - fractional grosze from an import or a conversion are rounded on the next save
        Basket basket = basket(item("A", 1, 10.005, 3.333));

        // when
        Item line = (Item) build(basket).lines().get(0);

        // then
        assertThat(line.unitPriceInput()).isEqualTo("10.01");
        assertThat(line.unitCostInput()).isEqualTo("3.33");
        assertThat(line.unitPrice()).isEqualTo(10.005);
        assertThat(line.unitCost()).isEqualTo(3.333);
    }
}
