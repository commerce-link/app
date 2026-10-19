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
import pl.commercelink.web.orders.Money;

import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
}
