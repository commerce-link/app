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
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import pl.commercelink.baskets.Basket;
import pl.commercelink.baskets.BasketItem;
import pl.commercelink.checkout.CheckoutError;
import pl.commercelink.checkout.CheckoutException;

import java.math.BigDecimal;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiscountServiceTest {

    private static final String STORE_ID = "store-1";
    private static final String CODE = "LATO-10";
    private static final String PROVIDER = "paynow";

    @Mock
    private DiscountCodeResolver discountCodeResolver;
    @Mock
    private PromotionPicker promotionPicker;
    @Mock
    private DiscountCodesRepository discountCodesRepository;

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

    @Test
    void codeIsResolvedAndWrittenToTheBasketWithoutLookingAtPromotions() {
        // given
        BasketItem gpu = gpu();
        Basket basket = basket(gpu);
        DiscountCode code = new DiscountCode();
        code.setCode(CODE);
        when(discountCodeResolver.resolve(basket, CODE, PROVIDER)).thenReturn(AppliedDiscount.of(code, tenOff(gpu)));

        // when
        discountService.apply(basket, CODE, PROVIDER);

        // then
        verifyNoInteractions(promotionPicker);
        assertThat(gpu.getUnitPrice()).isEqualTo(90.00);
        assertThat(basket.getDiscountCode()).isEqualTo(CODE);
        assertThat(basket.getDiscountAmount()).isEqualTo(10.00);
    }

    @Test
    void rejectedCodeIsNotReplacedByAPromotion() {
        // given
        Basket basket = basket(gpu());
        when(discountCodeResolver.resolve(basket, CODE, PROVIDER))
                .thenThrow(new CheckoutException(CheckoutError.DiscountCodeNotFound, "discount code LATO-10 does not exist"));

        // when / then
        assertThatThrownBy(() -> discountService.apply(basket, CODE, PROVIDER))
                .isInstanceOfSatisfying(CheckoutException.class,
                        e -> assertThat(e.getError()).isEqualTo(CheckoutError.DiscountCodeNotFound));
        verifyNoInteractions(promotionPicker);
        assertUntouched(basket);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void basketWithoutCodeGetsThePickedPromotions(String code) {
        // given
        BasketItem gpu = gpu();
        Basket basket = basket(gpu);
        when(promotionPicker.pick(basket)).thenReturn(Optional.of(AppliedDiscount.ofPromotions(tenOff(gpu))));

        // when
        discountService.apply(basket, code, PROVIDER);

        // then
        verifyNoInteractions(discountCodeResolver);
        assertThat(gpu.getUnitPrice()).isEqualTo(90.00);
        assertThat(basket.getDiscountCode()).isNull();
        assertThat(basket.getDiscountAmount()).isEqualTo(10.00);
    }

    @Test
    void basketWithoutCodeOrPromotionStaysUntouched() {
        // given
        Basket basket = basket(gpu());
        when(promotionPicker.pick(basket)).thenReturn(Optional.empty());

        // when
        discountService.apply(basket, null, PROVIDER);

        // then
        assertUntouched(basket);
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

    private static void assertUntouched(Basket basket) {
        assertThat(basket.getBasketItems()).extracting(BasketItem::getUnitPrice).containsExactly(100.00);
        assertThat(basket.getDiscountCode()).isNull();
        assertThat(basket.getDiscountAmount()).isZero();
    }

    private static DiscountCode used(int maxUses, int usedCount) {
        DiscountCode code = new DiscountCode();
        code.setStoreId(STORE_ID);
        code.setCode(CODE);
        code.setMaxUses(maxUses);
        code.setUsedCount(usedCount);
        return code;
    }

    private static DiscountCalculation tenOff(BasketItem item) {
        return new DiscountCalculation(List.of(new DiscountedLine(item, 90.00)), new BigDecimal("10.00"));
    }

    private static BasketItem gpu() {
        return new BasketItem("pim-GPU", "GPU", "MFN-GPU", "Karty graficzne", 100.00, 0, 1, "catalog-1", 2, false);
    }

    private static Basket basket(BasketItem... items) {
        Basket basket = new Basket();
        basket.setStoreId(STORE_ID);
        basket.setBasketItems(new LinkedList<>(List.of(items)));
        return basket;
    }
}
