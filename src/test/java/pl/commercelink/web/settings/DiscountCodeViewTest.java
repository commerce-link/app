package pl.commercelink.web.settings;

import org.junit.jupiter.api.Test;
import pl.commercelink.discounts.DiscountCode;
import pl.commercelink.discounts.DiscountCodeStatus;
import pl.commercelink.discounts.DiscountScope;
import pl.commercelink.discounts.DiscountType;
import pl.commercelink.discounts.Scopes;
import pl.commercelink.discounts.TargetValue;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class DiscountCodeViewTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 24, 12, 0);
    private static final String BASE = "/dashboard/store/discounts";
    private static final Map<String, String> GATEWAYS = Map.of("paynow", "PayNow");

    @Test
    void percentageWithThresholdShowsBothNumbers() {
        // given
        DiscountCode code = code("LATO-10", DiscountType.Percentage, 10);
        code.setMinOrderValue(500);

        // when
        DiscountCodeView view = DiscountCodeView.of(code, NOW, GATEWAYS, BASE);

        // then
        assertThat(view.percentage()).isTrue();
        assertThat(view.value()).isEqualTo("10");
        assertThat(view.minOrderValue()).isEqualTo("500,00");
    }

    @Test
    void fractionalPercentageKeepsItsDecimals() {
        // when
        DiscountCodeView view = DiscountCodeView.of(code("LATO-12", DiscountType.Percentage, 12.5), NOW, GATEWAYS, BASE);

        // then
        assertThat(view.value()).isEqualTo("12,5");
    }

    @Test
    void amountWithoutThresholdShowsCentsAndNoThreshold() {
        // when
        DiscountCodeView view = DiscountCodeView.of(code("GPU-50", DiscountType.Amount, 1250), NOW, GATEWAYS, BASE);

        // then
        assertThat(view.percentage()).isFalse();
        assertThat(view.value()).isEqualTo("1 250,00");
        assertThat(view.minOrderValue()).isNull();
    }

    @Test
    void showsThreeValuesOfAListAndCountsTheRest() {
        // given
        DiscountCode code = code("GPU-50", DiscountType.Amount, 50);
        code.setScope(Scopes.categories("Karty graficzne", "Procesory", "Płyty główne", "Zasilacze"));

        // when
        DiscountCodeView view = DiscountCodeView.of(code, NOW, GATEWAYS, BASE);

        // then
        assertThat(view.scope().everything()).isFalse();
        assertThat(view.scope().parts()).extracting(DiscountScopeView.Part::kind, DiscountScopeView.Part::shownNames,
                        DiscountScopeView.Part::more)
                .containsExactly(tuple("categories", List.of("Karty graficzne", "Procesory", "Płyty główne"), 1));
        assertThat(view.scope().parts().get(0).values()).extracting(TargetValue::getName)
                .containsExactly("Karty graficzne", "Procesory", "Płyty główne", "Zasilacze");
    }

    @Test
    void everyFilledListIsAPartInTheOrderCategoriesBrandsProducts() {
        // given
        DiscountCode code = code("ASUS-10", DiscountType.Percentage, 10);
        code.setScope(new DiscountScope(List.of("Monitory"), List.of("ASUS"),
                List.of(new TargetValue("PIM-1", "Monitor ASUS 27"))));

        // when
        DiscountCodeView view = DiscountCodeView.of(code, NOW, GATEWAYS, BASE);

        // then
        assertThat(view.scope().parts()).extracting(DiscountScopeView.Part::kind, DiscountScopeView.Part::shownNames)
                .containsExactly(tuple("categories", List.of("Monitory")), tuple("brands", List.of("ASUS")),
                        tuple("products", List.of("Monitor ASUS 27")));
        assertThat(view.scope().parts().get(2).values()).extracting(TargetValue::getId).containsExactly("PIM-1");
    }

    @Test
    void aWholeBasketCodeHasNoParts() {
        // when
        DiscountCodeView view = DiscountCodeView.of(code("LATO-10", DiscountType.Percentage, 10), NOW, GATEWAYS, BASE);

        // then
        assertThat(view.scope().everything()).isTrue();
        assertThat(view.scope().parts()).isEmpty();
    }

    @Test
    void namesTheGatewayByItsDisplayNameOrStoredNameAndNoneForAny() {
        // given
        DiscountCode any = code("ANY-10", DiscountType.Percentage, 10);
        DiscountCode paynow = code("PAYNOW-10", DiscountType.Percentage, 10);
        paynow.setPaymentProvider("paynow");
        DiscountCode disconnected = code("GONE-10", DiscountType.Percentage, 10);
        disconnected.setPaymentProvider("stripe");

        // when / then
        assertThat(DiscountCodeView.of(any, NOW, GATEWAYS, BASE).paymentProvider()).isNull();
        assertThat(DiscountCodeView.of(paynow, NOW, GATEWAYS, BASE).paymentProvider()).isEqualTo("PayNow");
        assertThat(DiscountCodeView.of(disconnected, NOW, GATEWAYS, BASE).paymentProvider()).isEqualTo("stripe");
    }

    @Test
    void theUsageBarFillsUpToTheLimitAndAnUnlimitedCodeHasNone() {
        // given
        DiscountCode partly = code("A-100", DiscountType.Percentage, 10);
        partly.setMaxUses(100);
        partly.setUsedCount(6);
        DiscountCode over = code("B-2", DiscountType.Percentage, 10);
        over.setMaxUses(2);
        over.setUsedCount(3);
        DiscountCode unlimited = code("C-0", DiscountType.Percentage, 10);
        unlimited.setUsedCount(241);

        // when
        DiscountCodeView partlyView = DiscountCodeView.of(partly, NOW, GATEWAYS, BASE);
        DiscountCodeView overView = DiscountCodeView.of(over, NOW, GATEWAYS, BASE);
        DiscountCodeView unlimitedView = DiscountCodeView.of(unlimited, NOW, GATEWAYS, BASE);

        // then
        assertThat(partlyView.limited()).isTrue();
        assertThat(partlyView.usagePercent()).isEqualTo(6);
        assertThat(overView.usagePercent()).isEqualTo(100);
        assertThat(unlimitedView.limited()).isFalse();
        assertThat(unlimitedView.usedCount()).isEqualTo(241);
    }

    @Test
    void validityIsShownAsDates() {
        // given
        DiscountCode both = code("BF-2026", DiscountType.Percentage, 20);
        both.setValidFrom(LocalDateTime.of(2026, 11, 27, 0, 0));
        both.setValidTo(LocalDateTime.of(2026, 11, 30, 23, 59, 59));
        DiscountCode none = code("WITAJ-5", DiscountType.Percentage, 5);

        // when
        DiscountCodeView bothView = DiscountCodeView.of(both, NOW, GATEWAYS, BASE);
        DiscountCodeView noneView = DiscountCodeView.of(none, NOW, GATEWAYS, BASE);

        // then
        assertThat(bothView.validFrom()).isEqualTo("27.11.2026");
        assertThat(bothView.validTo()).isEqualTo("30.11.2026");
        assertThat(noneView.validFrom()).isNull();
        assertThat(noneView.validTo()).isNull();
    }

    @Test
    void statusComesFromTheMoment() {
        // given
        DiscountCode scheduled = code("BF-2026", DiscountType.Percentage, 20);
        scheduled.setValidFrom(NOW.plusDays(30));
        DiscountCode disabled = code("ASUS-15", DiscountType.Percentage, 15);
        disabled.setActive(false);

        // when
        DiscountCodeView scheduledView = DiscountCodeView.of(scheduled, NOW, GATEWAYS, BASE);
        DiscountCodeView disabledView = DiscountCodeView.of(disabled, NOW, GATEWAYS, BASE);

        // then
        assertThat(scheduledView.status()).isEqualTo(DiscountCodeStatus.Scheduled);
        assertThat(disabledView.status()).isEqualTo(DiscountCodeStatus.Disabled);
    }

    @Test
    void linksToTheCodeDetails() {
        // when
        DiscountCodeView view = DiscountCodeView.of(code("LATO-10", DiscountType.Percentage, 10), NOW, GATEWAYS, BASE);

        // then
        assertThat(view.code()).isEqualTo("LATO-10");
        assertThat(view.name()).isEqualTo("Kampania LATO-10");
        assertThat(view.href()).isEqualTo("/dashboard/store/discounts/LATO-10");
    }

    private static DiscountCode code(String code, DiscountType type, double value) {
        DiscountCode discountCode = new DiscountCode();
        discountCode.setStoreId("store-1");
        discountCode.setCode(code);
        discountCode.setName("Kampania " + code);
        discountCode.setType(type);
        discountCode.setValue(value);
        discountCode.setActive(true);
        return discountCode;
    }
}
