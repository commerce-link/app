package pl.commercelink.web.clientoffer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.baskets.Basket;
import pl.commercelink.baskets.BasketItem;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.payments.PaymentProviderFactory;
import pl.commercelink.stores.BankAccount;
import pl.commercelink.stores.CheckoutConfiguration;
import pl.commercelink.stores.DeliveryOption;
import pl.commercelink.stores.Store;
import pl.commercelink.web.clientoffer.ClientOfferPage.ShippingMode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ClientOfferPageFactoryTest {

    private static final Locale PL = Locale.forLanguageTag("pl-PL");

    @Mock
    private PaymentProviderFactory paymentProviderFactory;

    @Test
    void amountsAreFormattedWithoutCurrency() {
        // given
        Store store = storeWithDelivery(delivery("d1", "Kurier", 29.99, ShipmentType.Courier));
        Basket basket = basket(item("MFN-1", "CPU", 449.0, 2, null));
        basket.setDeliveryOptionId("d1");

        // when
        ClientOfferPage page = factory().build(basket, store, false, PL);

        // then
        assertThat(page.lines()).hasSize(1);
        assertThat(page.lines().get(0).unitPrice()).isEqualTo("449,00");
        assertThat(page.lines().get(0).amount()).isEqualTo("898,00");
        assertThat(page.delivery().price()).isEqualTo("29,99");
        assertThat(page.total()).isEqualTo("927,99");
    }

    @Test
    void variantGroupListsOptionsWithSignedDeltas() {
        // given
        Basket basket = basket(item("A", "Dysk", 2899.0, 1, "Dysk"), item("B", "Dysk", 3899.0, 1, "Dysk"),
                item("C", "Dysk", 2449.0, 1, "Dysk"));

        // when
        ClientOfferPage page = factory().build(basket, new Store(), false, PL);

        // then
        assertThat(page.hasGroups()).isTrue();
        ClientOfferPage.Line line = page.lines().get(0);
        assertThat(line.isGroup()).isTrue();
        assertThat(line.groupId()).isEqualTo("Dysk");
        assertThat(line.options()).extracting(ClientOfferPage.Option::delta)
                .containsExactly(null, "+ 1 000,00", "− 450,00");
        assertThat(line.options()).extracting(ClientOfferPage.Option::selected).containsExactly(true, false, false);
    }

    @Test
    void variantGroupRowIsAnchoredByItsPlaceAmongTheLines() {
        // given — the group name is free text with a space and a Polish letter, so it cannot be the id
        Basket basket = basket(item("P", "Laptop", 4999.0, 1, null), item("A", "Monitor", 899.0, 2, "Monitor do biura ł"),
                item("B", "Monitor", 1149.0, 2, "Monitor do biura ł"));

        // when
        ClientOfferPage page = factory().build(basket, new Store(), false, PL);

        // then
        assertThat(page.lines()).extracting(ClientOfferPage.Line::anchor).containsExactly(null, "variant-1");
        assertThat(ClientOfferPageFactory.variantAnchor(basket, "Monitor do biura ł")).contains("variant-1");
        assertThat(ClientOfferPageFactory.variantAnchor(basket, "nope")).isEmpty();
    }

    @Test
    void optionOfTheSamePriceIsMarkedInsteadOfAZeroDelta() {
        // given
        Basket basket = basket(item("A", "Dysk", 2899.0, 1, "Dysk"), item("B", "Dysk", 2899.0, 1, "Dysk"),
                item("C", "Dysk", 3899.0, 1, "Dysk"));

        // when
        ClientOfferPage page = factory().build(basket, new Store(), false, PL);

        // then
        List<ClientOfferPage.Option> options = page.lines().get(0).options();
        assertThat(options).extracting(ClientOfferPage.Option::samePrice).containsExactly(false, true, false);
        assertThat(options).extracting(ClientOfferPage.Option::delta).containsExactly(null, null, "+ 1 000,00");
    }

    @Test
    void storeWithoutCheckoutConfigurationHasNoDelivery() {
        // given
        Basket basket = basket(item("MFN-1", "CPU", 100.0, 1, null));
        basket.setDeliveryOptionId("d1");
        Store store = new Store();
        store.setCheckoutConfiguration(null);

        // when
        ClientOfferPage page = factory().build(basket, store, false, PL);

        // then
        assertThat(page.delivery()).isNull();
        assertThat(page.total()).isEqualTo("100,00");
    }

    @Test
    void personalCollectionIsMarked() {
        // given
        Store store = storeWithDelivery(delivery("d1", "Odbior", 0.0, ShipmentType.PersonalCollection));
        Basket basket = basket(item("MFN-1", "CPU", 100.0, 1, null));
        basket.setDeliveryOptionId("d1");

        // when
        ClientOfferPage page = factory().build(basket, store, false, PL);

        // then
        assertThat(page.delivery().personalCollection()).isTrue();
        assertThat(page.delivery().name()).isEqualTo("Odbior");
    }

    @Test
    void submittedDataJoinsNamesWithoutNull() {
        // given
        Basket basket = basket(item("MFN-1", "CPU", 100.0, 1, null));
        BillingDetails billing = billing("Jan", null);
        basket.setBillingDetails(billing);
        basket.setShippingDetails(ClientOfferDataForm.shippingCopyOf(billing));

        // when
        ClientOfferPage page = factory().build(basket, new Store(), false, PL);

        // then
        assertThat(page.submitted()).isTrue();
        assertThat(page.data().billing().fullName()).isEqualTo("Jan");
        assertThat(page.data().billing().street()).isEqualTo("Prosta 1");
        assertThat(page.data().billing().taxId()).isEqualTo("1234567890");
    }

    @Test
    void shippingEqualToBillingIsShownAsSame() {
        // given
        BillingDetails billing = billing("Jan", "Kowalski");
        Basket same = basket(item("MFN-1", "CPU", 100.0, 1, null));
        same.setBillingDetails(billing);
        same.setShippingDetails(ClientOfferDataForm.shippingCopyOf(billing));
        Basket other = basket(item("MFN-1", "CPU", 100.0, 1, null));
        other.setBillingDetails(billing);
        ShippingDetails elsewhere = ClientOfferDataForm.shippingCopyOf(billing);
        elsewhere.setCity("Gdansk");
        other.setShippingDetails(elsewhere);

        // when
        ClientOfferPage samePage = factory().build(same, new Store(), false, PL);
        ClientOfferPage otherPage = factory().build(other, new Store(), false, PL);

        // then
        assertThat(samePage.data().shippingMode()).isEqualTo(ShippingMode.SAME);
        assertThat(otherPage.data().shippingMode()).isEqualTo(ShippingMode.OTHER);
        assertThat(otherPage.data().shipping().city()).isEqualTo("Gdansk");
    }

    @Test
    void paymentIsLockedUntilDataIsGiven() {
        // given
        Basket basket = basket(item("MFN-1", "CPU", 100.0, 1, null));

        // when
        ClientOfferPage page = factory().build(basket, new Store(), false, PL);

        // then
        assertThat(page.submitted()).isFalse();
        assertThat(page.data()).isNull();
        assertThat(page.payment().unlocked()).isFalse();
    }

    @Test
    void bankAccountIsGroupedAndHidesPlnCurrency() {
        // given
        Basket basket = basket(item("MFN-1", "CPU", 100.0, 1, null));
        Store store = new Store();
        BankAccount account = new BankAccount();
        account.setIban("PL61109010140000071219812874");
        account.setCurrency("PLN");
        account.setAccountHolder("Sklep sp. z o.o.");
        account.setBankName("Bank");
        account.setSwiftCode("SWIFT");
        store.addBankAccount(account, true);

        // when
        ClientOfferPage.Bank pln = factory().build(basket, store, false, PL).payment().bank();
        account.setCurrency("EUR");
        ClientOfferPage.Bank eur = factory().build(basket, store, false, PL).payment().bank();

        // then
        assertThat(pln.ibanGrouped()).isEqualTo("PL61 1090 1014 0000 0712 1981 2874");
        assertThat(pln.ibanRaw()).isEqualTo("PL61109010140000071219812874");
        assertThat(pln.currency()).isNull();
        assertThat(pln.transferTitle()).isEqualTo(basket.getBasketId());
        assertThat(pln.holder()).isEqualTo("Sklep sp. z o.o.");
        assertThat(eur.currency()).isEqualTo("EUR");
    }

    @Test
    void storeWithoutPaymentsHasNoOptions() {
        // given
        Basket basket = basket(item("MFN-1", "CPU", 100.0, 1, null));

        // when
        ClientOfferPage.Payment payment = factory().build(basket, new Store(), false, PL).payment();

        // then
        assertThat(payment.options()).isEmpty();
        assertThat(payment.defaultOption()).isNull();
    }

    @Test
    void validUntilIsFormattedInTheGivenLocale() {
        // given
        Basket basket = basket(item("MFN-1", "CPU", 100.0, 1, null));
        basket.setExpiresAt(LocalDateTime.of(2030, 3, 5, 14, 30));

        // when
        ClientOfferPage page = factory().build(basket, new Store(), true, PL);

        // then
        assertThat(page.validUntil()).isEqualTo("5 marca 2030, 14:30");
        assertThat(page.shortId()).isEqualTo("12345678");
        assertThat(page.canEdit()).isTrue();
    }

    private ClientOfferPageFactory factory() {
        return new ClientOfferPageFactory(paymentProviderFactory);
    }

    private static Basket basket(BasketItem... items) {
        Basket basket = new Basket();
        basket.setStoreId("store1");
        basket.setBasketId("12345678-aaaa-bbbb-cccc-1234567890ab");
        basket.setBasketItems(List.of(items));
        return basket;
    }

    private static BasketItem item(String mfn, String category, double price, long qty, String groupId) {
        BasketItem item = new BasketItem("pim-" + mfn, "Product " + mfn, mfn, category, price, 0, qty, null, 3, false);
        item.setVariantGroupId(groupId);
        return item;
    }

    private static DeliveryOption delivery(String id, String name, double price, ShipmentType type) {
        DeliveryOption option = new DeliveryOption();
        option.setId(id);
        option.setName(name);
        option.setPrice(price);
        option.setType(type);
        return option;
    }

    private static Store storeWithDelivery(DeliveryOption option) {
        Store store = new Store();
        CheckoutConfiguration configuration = new CheckoutConfiguration();
        configuration.addDeliveryOption(option);
        store.setCheckoutConfiguration(configuration);
        return store;
    }

    private static BillingDetails billing(String name, String surname) {
        BillingDetails billing = new BillingDetails();
        billing.setName(name);
        billing.setSurname(surname);
        billing.setTaxId("1234567890");
        billing.setStreetAndNumber("Prosta 1");
        billing.setPostalCode("00-001");
        billing.setCity("Warszawa");
        billing.setCountry("PL");
        billing.setEmail("jan@example.com");
        billing.setPhone("500100200");
        return billing;
    }
}
