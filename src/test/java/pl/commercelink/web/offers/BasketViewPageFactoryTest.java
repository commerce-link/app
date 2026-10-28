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
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.OrderSource;
import pl.commercelink.orders.OrderSourceType;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.orders.fulfilment.FulfilmentType;
import pl.commercelink.stores.CheckoutConfiguration;
import pl.commercelink.stores.DeliveryOption;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.taxonomy.CategoryLocalizer;
import pl.commercelink.web.offers.BasketViewPage.DeliveryState;
import pl.commercelink.web.orders.Money;

import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BasketViewPageFactoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 14, 10, 0);
    private static final Locale PL = Locale.forLanguageTag("pl");
    private static final String ID = "8f3a21c7-1b2d-4e5f-9a0b-1c2d3e4f5a6b";
    private static final String NBSP = " ";

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private CategoryLocalizer categoryLocalizer;

    private BasketViewPageFactory factory;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        factory = new BasketViewPageFactory(storesRepository, categoryLocalizer, source, 14);

        Store store = new Store();
        CheckoutConfiguration checkout = new CheckoutConfiguration();
        DeliveryOption inpost = new DeliveryOption();
        inpost.setId("inpost");
        inpost.setName("Kurier InPost");
        inpost.setPrice(19.99);
        checkout.setDeliveryOptions(new LinkedList<>(List.of(inpost)));
        store.setCheckoutConfiguration(checkout);
        when(storesRepository.findById("store-1")).thenReturn(store);
    }

    private static Basket basket(BasketItem... items) {
        Basket basket = new Basket();
        basket.setStoreId("store-1");
        basket.setBasketId(ID);
        basket.setCreatedAt(NOW.minusDays(1));
        basket.setBasketItems(new LinkedList<>(List.of(items)));
        return basket;
    }

    private static BasketItem item(String name, String mfn, long qty, double unitPrice) {
        BasketItem item = new BasketItem();
        item.setName(name);
        item.setMfn(mfn);
        item.setCategory("laptops");
        item.setQty(qty);
        item.setUnitPrice(unitPrice);
        return item;
    }

    private static BasketItem service(String name, double unitPrice) {
        BasketItem item = new BasketItem();
        item.setName(name);
        item.setMfn("SRV-1");
        item.setQty(1);
        item.setUnitPrice(unitPrice);
        item.setService(true);
        return item;
    }

    private BasketViewPage build(Basket basket) {
        return factory.build(basket, null, PL, NOW);
    }

    private static BillingDetails billing(String name, String surname, String company, String street, String city) {
        BillingDetails details = new BillingDetails();
        details.setName(name);
        details.setSurname(surname);
        details.setCompanyName(company);
        details.setStreetAndNumber(street);
        details.setCity(city);
        return details;
    }

    private static ShippingDetails shipping(String name, String surname, String street, String city) {
        ShippingDetails details = new ShippingDetails();
        details.setName(name);
        details.setSurname(surname);
        details.setStreetAndNumber(street);
        details.setCity(city);
        return details;
    }

    @Test
    void totalsCountChosenItemsAndTheDelivery() {
        // given
        Basket basket = basket(item("Laptop", "L-1", 1, 6899.00), item("Monitor", "M-1", 2, 1749.00));
        basket.setDeliveryOptionId("inpost");

        // when
        BasketViewPage page = build(basket);

        // then
        assertThat(page.summary().items()).isEqualTo("10" + NBSP + "397,00");
        assertThat(page.summary().deliveryState()).isEqualTo(DeliveryState.CHOSEN);
        assertThat(page.summary().deliveryName()).isEqualTo("Kurier InPost");
        assertThat(page.summary().delivery()).isEqualTo("19,99");
        assertThat(page.summary().total()).isEqualTo("10" + NBSP + "416,99");
        assertThat(page.summary().net()).isEqualTo(Money.format(10416.99 / 1.23)).isEqualTo("8" + NBSP + "469,10");
        assertThat(page.summary().vat()).isEqualTo("1" + NBSP + "947,89");
        assertThat(page.total()).isEqualTo(page.summary().total());
        assertThat(page.itemsCount()).isEqualTo(2);
    }

    @Test
    void discountedBasketShowsItemsBeforeDiscountAndTheCode() {
        // given
        Basket basket = basket(item("Laptop", "L-1", 1, 900.00), item("Mysz", "M-1", 2, 50.00));
        basket.setDiscountCode("LATO-10");
        basket.setDiscountAmount(100.00);
        basket.setDeliveryOptionId("inpost");

        // when
        BasketViewPage page = build(basket);

        // then
        assertThat(page.summary().items()).isEqualTo("1" + NBSP + "100,00");
        assertThat(page.summary().discountCode()).isEqualTo("LATO-10");
        assertThat(page.summary().discount()).isEqualTo("100,00");
        assertThat(page.summary().total()).isEqualTo("1" + NBSP + "019,99");
        assertThat(page.products()).extracting(BasketViewPage.Row::unitPrice).containsExactly("900,00", "50,00");
    }

    @Test
    void basketWithoutCodeHasNoDiscount() {
        // given
        Basket basket = basket(item("Laptop", "L-1", 1, 900.00));

        // when
        BasketViewPage page = build(basket);

        // then
        assertThat(page.summary().discountCode()).isNull();
        assertThat(page.summary().items()).isEqualTo("900,00");
    }

    @Test
    void unknownDeliveryOptionCountsAsZeroAndIsFlagged() {
        // given
        Basket basket = basket(item("Laptop", "L-1", 1, 6899.00));
        basket.setDeliveryOptionId("gone");

        // when
        BasketViewPage page = build(basket);

        // then
        assertThat(page.summary().deliveryState()).isEqualTo(DeliveryState.UNKNOWN);
        assertThat(page.summary().delivery()).isEqualTo("0,00");
        assertThat(page.summary().total()).isEqualTo(page.summary().items());
    }

    @Test
    void basketWithoutDeliveryOptionSaysNone() {
        // given
        Basket basket = basket(item("Laptop", "L-1", 1, 6899.00));

        // when
        BasketViewPage page = build(basket);

        // then
        assertThat(page.summary().deliveryState()).isEqualTo(DeliveryState.NONE);
        assertThat(page.summary().deliveryName()).isNull();
    }

    @Test
    void rowsShowTheBasketWithTheShopPrices() {
        // given
        when(categoryLocalizer.localize("laptops", null)).thenReturn("Laptopy");
        Basket basket = basket(item("Laptop", "L-1", 2, 6899.00));

        // when
        BasketViewPage.Row row = build(basket).products().get(0);

        // then: no supplier cost, margin or stock; the client found them of no use on a basket priced by the shop
        assertThat(row).isEqualTo(new BasketViewPage.Row("Laptop", "Laptopy", "L-1", 2, "6" + NBSP + "899,00", false));
    }

    @Test
    void servicesAreListedApart() {
        // given
        Basket basket = basket(service("Montaż", 199.00));

        // when
        BasketViewPage page = build(basket);

        // then
        assertThat(page.products()).isEmpty();
        assertThat(page.services()).hasSize(1);
        assertThat(page.services().get(0).service()).isTrue();
    }

    @Test
    void emptyBasketHasNoItems() {
        // given
        Basket basket = basket();

        // when
        BasketViewPage page = build(basket);

        // then
        assertThat(page.itemsCount()).isZero();
        assertThat(page.empty()).isTrue();
        assertThat(page.total()).isEqualTo("0,00");
    }

    @Test
    void onlyTheChosenVariantAndPlainItemsAreCounted() {
        // given
        BasketItem cheap = item("Laptop A", "L-A", 1, 3000.00);
        cheap.setVariantGroupId("g1");
        BasketItem chosen = item("Laptop B", "L-B", 1, 4000.00);
        chosen.setVariantGroupId("g1");
        chosen.setVariantSelected(true);
        BasketItem plain = item("Monitor", "M-1", 2, 1000.00);

        // when
        BasketViewPage page = build(basket(cheap, chosen, plain));

        // then
        assertThat(page.itemsCount()).isEqualTo(2);
        assertThat(page.products()).extracting(BasketViewPage.Row::name).containsExactly("Laptop B", "Monitor");
        assertThat(page.summary().items()).isEqualTo("6" + NBSP + "000,00");
    }

    @Test
    void deletionPillCountsDaysWhenTwoAreLeft() {
        // given
        Basket basket = basket(item("Laptop", "L-1", 1, 6899.00));
        basket.setCreatedAt(NOW.minusDays(12));

        // when
        BasketViewPage page = build(basket);

        // then
        assertThat(page.deletionPill()).isEqualTo(new BasketViewPage.Pill("offers.basket.pill.deletion.days", 2L));
        assertThat(page.facts().deletion()).isEqualTo("16.10.2026 · dni: 2");
    }

    @Test
    void differentDeliveryEmailOrPhoneIsNotTheSameAddress() {
        // given
        Basket otherEmail = basket(item("Laptop", "L-1", 1, 6899.00));
        otherEmail.setBillingDetails(billing("Marta", "Zielińska", null, "Lipowa 1", "Łódź"));
        otherEmail.getBillingDetails().setEmail("a@x.pl");
        otherEmail.setShippingDetails(shipping("Marta", "Zielińska", "Lipowa 1", "Łódź"));
        otherEmail.getShippingDetails().setEmail("b@x.pl");
        Basket otherPhone = basket(item("Laptop", "L-1", 1, 6899.00));
        otherPhone.setBillingDetails(billing("Marta", "Zielińska", null, "Lipowa 1", "Łódź"));
        otherPhone.getBillingDetails().setPhone("+48 601");
        otherPhone.setShippingDetails(shipping("Marta", "Zielińska", "Lipowa 1", "Łódź"));
        otherPhone.getShippingDetails().setPhone("+48 602");

        // when
        BasketViewPage emailPage = build(otherEmail);
        BasketViewPage phonePage = build(otherPhone);

        // then
        assertThat(emailPage.customer().sameAddress()).isFalse();
        assertThat(phonePage.customer().sameAddress()).isFalse();
    }

    @Test
    void categoryKeyIsShownByItsLocalizedName() {
        // given
        when(categoryLocalizer.localize("laptops", null)).thenReturn("Laptopy");

        // when
        BasketViewPage page = build(basket(item("Laptop", "L-1", 1, 6899.00)));

        // then
        assertThat(page.products().get(0).category()).isEqualTo("Laptopy");
    }

    @Test
    void headerNamesTheClientFromBillingDetails() {
        // given
        Basket named = basket(item("Laptop", "L-1", 1, 6899.00));
        named.setBillingDetails(billing("Marta", "Zielińska", "Biuro Rachunkowe Zielińska", "Lipowa 1", "Łódź"));
        Basket guest = basket(item("Laptop", "L-1", 1, 6899.00));

        // when
        BasketViewPage namedPage = build(named);
        BasketViewPage guestPage = build(guest);

        // then
        assertThat(namedPage.client()).isEqualTo("Marta Zielińska · Biuro Rachunkowe Zielińska");
        assertThat(namedPage.hasClient()).isTrue();
        assertThat(guestPage.client()).isNull();
        assertThat(guestPage.hasClient()).isFalse();
    }

    @Test
    void sameDeliveryAddressIsSaidOnce() {
        // given
        Basket same = basket(item("Laptop", "L-1", 1, 6899.00));
        same.setBillingDetails(billing("Marta", "Zielińska", null, "Lipowa 1", "Łódź"));
        same.setShippingDetails(shipping("Marta", "Zielińska", "Lipowa 1", "Łódź"));
        Basket different = basket(item("Laptop", "L-1", 1, 6899.00));
        different.setBillingDetails(billing("Marta", "Zielińska", null, "Lipowa 1", "Łódź"));
        different.setShippingDetails(shipping("Marta", "Zielińska", "Klonowa 7", "Łódź"));

        // when
        BasketViewPage samePage = build(same);
        BasketViewPage differentPage = build(different);

        // then
        assertThat(samePage.customer().sameAddress()).isTrue();
        assertThat(differentPage.customer().sameAddress()).isFalse();
        assertThat(differentPage.customer().empty()).isFalse();
        assertThat(build(basket()).customer().empty()).isTrue();
    }

    @Test
    void deletionPillAppearsInTheLastTwoDays() {
        // given
        Basket tomorrow = basket(item("Laptop", "L-1", 1, 6899.00));
        tomorrow.setCreatedAt(NOW.minusDays(13));
        Basket later = basket(item("Laptop", "L-1", 1, 6899.00));
        later.setCreatedAt(NOW.minusDays(5));
        Basket overdue = basket(item("Laptop", "L-1", 1, 6899.00));
        overdue.setCreatedAt(NOW.minusDays(20));

        // when
        BasketViewPage tomorrowPage = build(tomorrow);
        BasketViewPage laterPage = build(later);
        BasketViewPage overduePage = build(overdue);

        // then
        assertThat(tomorrowPage.deletionPill().key()).isEqualTo("offers.basket.pill.deletion.tomorrow");
        assertThat(tomorrowPage.facts().deletion()).isEqualTo("15.10.2026 · jutro");
        assertThat(laterPage.deletionPill()).isNull();
        assertThat(laterPage.facts().deletion()).isEqualTo("23.10.2026 · dni: 9");
        assertThat(overduePage.deletionPill().key()).isEqualTo("offers.basket.pill.deletion.today");
        assertThat(tomorrowPage.facts().cleanupDays()).isEqualTo(14);
    }

    @Test
    void safeReturnToIsKeptAndCarriedToTheOrderConfirmation() {
        // given
        String returnTo = "/dashboard/offers?segment=baskets&q=8f";

        // when
        BasketViewPage page = factory.build(basket(item("Laptop", "L-1", 1, 6899.00)), returnTo, PL, NOW);

        // then
        assertThat(page.backHref()).isEqualTo(returnTo);
        assertThat(page.orderHref()).isEqualTo("/dashboard/basket/view/" + ID
                + "/order?returnTo=%2Fdashboard%2Foffers%3Fsegment%3Dbaskets%26q%3D8f");
        assertThat(page.offerHref()).isEqualTo("/dashboard/offer/new?intent=copy&sourceId=" + ID);
    }

    @Test
    void foreignReturnToFallsBackToTheBasketsSegment() {
        // given
        List<String> foreign = List.of("https://evil.example", "//evil", "/dashboard/offers\r\nX", "/dashboard/offers?{x}");

        for (String returnTo : foreign) {
            // when
            BasketViewPage page = factory.build(basket(item("Laptop", "L-1", 1, 6899.00)), returnTo, PL, NOW);

            // then
            assertThat(page.backHref()).isEqualTo(BasketViewPageFactory.BASKETS_PATH);
            assertThat(page.orderHref()).isEqualTo("/dashboard/basket/view/" + ID + "/order");
        }
    }

    @Test
    void factsNameSourceAffiliateAndFulfilment() {
        // given
        Basket basket = basket(item("Laptop", "L-1", 1, 6899.00));
        basket.setSource(new OrderSource("Sklep", OrderSourceType.WebStore));
        basket.setAffiliateId("blog-ksiegowych");
        basket.setFulfilmentType(FulfilmentType.WarehouseFulfilment);

        // when
        BasketViewPage page = build(basket);

        // then
        assertThat(page.facts().sourceKey()).isEqualTo("order.source.type.WebStore");
        assertThat(page.facts().affiliate()).isEqualTo("blog-ksiegowych");
        assertThat(page.facts().fulfilmentKey()).isEqualTo("store.fulfilment.type.WarehouseFulfilment");
    }
}
