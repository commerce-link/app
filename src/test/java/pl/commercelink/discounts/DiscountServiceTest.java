package pl.commercelink.discounts;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.amazonaws.AmazonServiceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import pl.commercelink.pim.ScopedPimCatalog;
import pl.commercelink.products.information.StorePimAliases;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import pl.commercelink.baskets.Basket;
import pl.commercelink.baskets.BasketItem;
import pl.commercelink.checkout.CheckoutError;
import pl.commercelink.checkout.CheckoutException;

import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiscountServiceTest {

    private static final String STORE_ID = "store-1";
    private static final String CODE = "LATO-10";
    private static final String PROVIDER = "paynow";

    @Mock
    private DiscountCodesRepository discountCodesRepository;

    @Spy
    private StorePimAliases aliases = new StorePimAliases(ScopedPimCatalog.of(
            ScopedPimCatalog.globalEntry("pim-GPU-source", "5900000000011", "M1", "cat"),
            ScopedPimCatalog.cloneEntry("pim-GPU", STORE_ID, "pim-GPU-source", "5900000000011", "M1", "cat")));

    @InjectMocks
    private DiscountService discountService;

    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    @BeforeEach
    void attachLogs() {
        logs.start();
        serviceLogger().addAppender(logs);
    }

    @AfterEach
    void detachLogs() {
        serviceLogger().detachAppender(logs);
    }

    private static Logger serviceLogger() {
        return (Logger) LoggerFactory.getLogger(DiscountService.class);
    }

    static Stream<Arguments> rejections() {
        return Stream.of(
                arguments("missing", null, CheckoutError.DiscountCodeNotFound),
                arguments("disabled", (Consumer<DiscountCode>) c -> c.setActive(false), CheckoutError.DiscountCodeInactive),
                arguments("exhausted", (Consumer<DiscountCode>) c -> {
                    c.setMaxUses(1);
                    c.setUsedCount(1);
                }, CheckoutError.DiscountCodeExhausted),
                arguments("expired", (Consumer<DiscountCode>) c -> c.setValidTo(LocalDateTime.of(2020, 1, 31, 23, 59, 59)),
                        CheckoutError.DiscountCodeExpired),
                arguments("not yet valid", (Consumer<DiscountCode>) c -> c.setValidFrom(LocalDateTime.of(2099, 1, 1, 0, 0)),
                        CheckoutError.DiscountCodeNotYetValid),
                arguments("other gateway", (Consumer<DiscountCode>) c -> c.setPaymentProvider("przelewy24"),
                        CheckoutError.DiscountCodePaymentMethodMismatch),
                arguments("below minimum", (Consumer<DiscountCode>) c -> c.setMinOrderValue(10_000),
                        CheckoutError.DiscountCodeMinimumNotReached),
                arguments("nothing covered", (Consumer<DiscountCode>) c -> {
                    c.setTarget(DiscountTarget.Brand);
                    c.setTargetValues(List.of(new TargetValue("Initech", "Initech")));
                }, CheckoutError.DiscountCodeNotApplicable));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rejections")
    void applyRejectsWithEachReason(String reason, Consumer<DiscountCode> change, CheckoutError expected) {
        // given
        if (change == null) {
            when(discountCodesRepository.find(STORE_ID, CODE)).thenReturn(Optional.empty());
        } else {
            DiscountCode code = percentageOnBrandAcme();
            change.accept(code);
            when(discountCodesRepository.find(STORE_ID, CODE)).thenReturn(Optional.of(code));
        }
        Basket basket = basket();

        // when / then
        assertThatThrownBy(() -> discountService.apply(basket, CODE, PROVIDER))
                .isInstanceOfSatisfying(CheckoutException.class, e -> assertThat(e.getError()).isEqualTo(expected));
        assertThat(basket.getBasketItems()).extracting(BasketItem::getUnitPrice).containsExactly(129.90, 50.00, 100.00);
        assertThat(basket.getDiscountCode()).isNull();
        assertThat(basket.getDiscountAmount()).isZero();
    }

    @Test
    void minimumNotReachedCarriesTheThreshold() {
        // given
        DiscountCode code = percentageOnBrandAcme();
        code.setMinOrderValue(1500);
        when(discountCodesRepository.find(STORE_ID, CODE)).thenReturn(Optional.of(code));

        // when / then
        assertThatThrownBy(() -> discountService.apply(basket(), CODE, PROVIDER))
                .isInstanceOfSatisfying(CheckoutException.class,
                        e -> assertThat(e.getMessageArgs()).extracting(Object::toString).containsExactly("1500.00"));
    }

    @Test
    void minimumCountsWholeBasketBeforeDiscount() {
        // given
        DiscountCode code = percentageOnBrandAcme();
        code.setMinOrderValue(520);
        when(discountCodesRepository.find(STORE_ID, CODE)).thenReturn(Optional.of(code));
        Basket basket = basket();

        // when
        discountService.apply(basket, CODE, PROVIDER);

        // then
        assertThat(basket.getDiscountCode()).isEqualTo(CODE);
    }

    @Test
    void basketEqualToMinimumIsAccepted() {
        // given
        DiscountCode code = percentageOnBrandAcme();
        code.setMinOrderValue(539.70);
        when(discountCodesRepository.find(STORE_ID, CODE)).thenReturn(Optional.of(code));
        Basket basket = basket();

        // when
        discountService.apply(basket, CODE, PROVIDER);

        // then
        assertThat(basket.getDiscountCode()).isEqualTo(CODE);
    }

    @Test
    void codeWithoutGatewayAcceptsAnyProvider() {
        // given
        when(discountCodesRepository.find(STORE_ID, CODE)).thenReturn(Optional.of(percentageOnBrandAcme()));
        Basket viaPaynow = basket();
        Basket viaPrzelewy24 = basket();

        // when
        discountService.apply(viaPaynow, CODE, PROVIDER);
        discountService.apply(viaPrzelewy24, CODE, "przelewy24");

        // then
        assertThat(viaPaynow.getDiscountCode()).isEqualTo(CODE);
        assertThat(viaPrzelewy24.getDiscountCode()).isEqualTo(CODE);
    }

    @Test
    void codeForTheCheckoutGatewayIsAccepted() {
        // given
        DiscountCode code = percentageOnBrandAcme();
        code.setPaymentProvider(PROVIDER);
        when(discountCodesRepository.find(STORE_ID, CODE)).thenReturn(Optional.of(code));
        Basket basket = basket();

        // when
        discountService.apply(basket, CODE, PROVIDER);

        // then
        assertThat(basket.getDiscountCode()).isEqualTo(CODE);
    }

    @Test
    void codeForTheSourceProductDiscountsTheClonePositionInTheBasket() {
        // given: the code was made for the global entry; the basket holds the store's clone (id "pim-GPU")
        DiscountCode code = new DiscountCode();
        code.setActive(true);
        code.setCode(CODE);
        code.setType(DiscountType.Percentage);
        code.setValue(10);
        code.setTarget(DiscountTarget.Product);
        code.setTargetValues(List.of(new TargetValue("pim-GPU-source", "GPU")));
        when(discountCodesRepository.find(STORE_ID, CODE)).thenReturn(Optional.of(code));
        Basket basket = basket();

        // when
        discountService.apply(basket, CODE, PROVIDER);

        // then
        assertThat(basket.getBasketItems()).extracting(BasketItem::getName, BasketItem::getUnitPrice)
                .contains(tuple("GPU", 116.91), tuple("RAM", 50.00));
    }

    @Test
    void applyDiscountsItemsAndStoresAmount() {
        // given
        when(discountCodesRepository.find(STORE_ID, CODE)).thenReturn(Optional.of(percentageOnBrandAcme()));
        Basket basket = basket();

        // when
        discountService.apply(basket, CODE, PROVIDER);

        // then
        assertThat(basket.getBasketItems()).extracting(BasketItem::getName, BasketItem::getUnitPrice)
                .containsExactly(tuple("GPU", 116.91), tuple("RAM", 50.00), tuple("Montaż", 100.00));
        assertThat(basket.getDiscountAmount()).isEqualTo(38.97);
    }

    @Test
    void applyStoresNormalizedCode() {
        // given
        when(discountCodesRepository.find(STORE_ID, CODE)).thenReturn(Optional.of(percentageOnBrandAcme()));
        Basket basket = basket();

        // when
        discountService.apply(basket, " lato-10 ", PROVIDER);

        // then
        assertThat(basket.getDiscountCode()).isEqualTo(CODE);
    }

    @Test
    void onlyEffectiveItemsAreDiscounted() {
        // given
        DiscountCode code = new DiscountCode();
        code.setCode(CODE);
        code.setActive(true);
        code.setType(DiscountType.Amount);
        code.setValue(10);
        when(discountCodesRepository.find(STORE_ID, CODE)).thenReturn(Optional.of(code));
        BasketItem skipped = product("Laptop A", 100.00, 1, "Acme");
        skipped.setVariantGroupId("g1");
        BasketItem chosen = product("Laptop B", 200.00, 1, "Acme");
        chosen.setVariantGroupId("g1");
        chosen.setVariantSelected(true);
        Basket basket = new Basket();
        basket.setStoreId(STORE_ID);
        basket.setBasketItems(new LinkedList<>(List.of(skipped, chosen)));

        // when
        discountService.apply(basket, CODE, PROVIDER);

        // then
        assertThat(skipped.getUnitPrice()).isEqualTo(100.00);
        assertThat(chosen.getUnitPrice()).isEqualTo(190.00);
        assertThat(basket.getDiscountAmount()).isEqualTo(10.00);
    }

    @Test
    void redeemWithinTheLimitLogsNothing() {
        // given
        when(discountCodesRepository.redeem(STORE_ID, CODE)).thenReturn(Optional.of(used(2, 2)));

        // when
        discountService.redeem(STORE_ID, CODE);

        // then
        verify(discountCodesRepository).redeem(STORE_ID, CODE);
        assertThat(logs.list).isEmpty();
    }

    @Test
    void redeemOverTheLimitRaisesAnError() {
        // given
        when(discountCodesRepository.redeem(STORE_ID, CODE)).thenReturn(Optional.of(used(2, 3)));

        // when
        discountService.redeem(STORE_ID, CODE);

        // then
        assertThat(logs.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getFormattedMessage()).contains(CODE, STORE_ID, "3", "2");
        });
    }

    @Test
    void redeemOfMissingCodeRaisesAnError() {
        // given
        when(discountCodesRepository.redeem(STORE_ID, CODE)).thenReturn(Optional.empty());

        // when
        discountService.redeem(STORE_ID, CODE);

        // then
        assertThat(logs.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getFormattedMessage()).contains(CODE, STORE_ID);
        });
    }

    @Test
    void redeemSwallowsRepositoryError() {
        // given
        when(discountCodesRepository.redeem(STORE_ID, CODE)).thenThrow(new AmazonServiceException("throttled"));

        // when
        discountService.redeem(STORE_ID, CODE);

        // then
        assertThat(logs.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getFormattedMessage()).contains(CODE, STORE_ID);
        });
    }

    private static DiscountCode used(int maxUses, int usedCount) {
        DiscountCode code = percentageOnBrandAcme();
        code.setMaxUses(maxUses);
        code.setUsedCount(usedCount);
        return code;
    }

    private static DiscountCode percentageOnBrandAcme() {
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

    private static Basket basket() {
        BasketItem service = product("Montaż", 100.00, 1, "Acme");
        service.setService(true);
        Basket basket = new Basket();
        basket.setStoreId(STORE_ID);
        basket.setBasketItems(new LinkedList<>(List.of(
                product("GPU", 129.90, 3, "Acme"),
                product("RAM", 50.00, 1, "Globex"),
                service)));
        return basket;
    }

    private static BasketItem product(String name, double unitPrice, long qty, String brand) {
        BasketItem item = new BasketItem("pim-" + name, name, "MFN-" + name, "Podzespoły", unitPrice, 0, qty, "catalog-1", 2, false);
        item.setBrand(brand);
        return item;
    }
}
