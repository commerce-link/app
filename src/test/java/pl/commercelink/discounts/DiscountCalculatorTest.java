package pl.commercelink.discounts;

import org.junit.jupiter.api.Test;
import pl.commercelink.baskets.BasketItem;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class DiscountCalculatorTest {

    @Test
    void discountForTheSourceCoversTheClone() {
        // given: the code names the global entry, the basket holds the store's clone
        DiscountCode code = percentage(10, DiscountTarget.Product, "pim-S");
        BasketItem clone = product("C", 100, 1);

        // when
        List<DiscountedLine> lines = DiscountCalculator.calculate(code, List.of(clone), aliasesOfS());

        // then
        assertThat(lines).hasSize(1);
        assertThat(DiscountCalculator.calculate(code, List.of(clone), Set::of)).isEmpty();
    }

    @Test
    void discountForTheCloneCoversTheSource() {
        // given
        DiscountCode code = percentage(10, DiscountTarget.Product, "pim-C");
        BasketItem source = product("S", 100, 1);

        // when / then
        assertThat(DiscountCalculator.calculate(code, List.of(source), aliasesOfS())).hasSize(1);
    }

    @Test
    void discountForAnUnrelatedProductCoversNeither() {
        // when / then
        assertThat(DiscountCalculator.calculate(percentage(10, DiscountTarget.Product, "pim-G"), List.of(product("C", 100, 1)), aliasesOfS())).isEmpty();
    }

    private static Function<String, Set<String>> aliasesOfS() {
        return id -> new HashSet<>(List.of(id, "pim-S", "pim-C"));
    }

    @Test
    void percentageRoundsHalfUp() {
        // given
        DiscountCode code = percentage(15);
        BasketItem gpu = product("GPU", 199.99, 1);
        BasketItem cable = product("Kabel", 0.10, 1);

        // when
        List<DiscountedLine> lines = DiscountCalculator.calculate(code, List.of(gpu, cable), Set::of);

        // then
        assertThat(lines).extracting(l -> l.item().getName(), DiscountedLine::unitPrice)
                .containsExactly(tuple("GPU", 169.99), tuple("Kabel", 0.09));
    }

    @Test
    void percentageNeverGoesBelowOneGrosz() {
        // given
        DiscountCode code = percentage(99);
        BasketItem sticker = product("Naklejka", 0.01, 1);
        BasketItem pad = product("Podkładka", 1.00, 1);

        // when
        List<DiscountedLine> lines = DiscountCalculator.calculate(code, List.of(sticker, pad), Set::of);

        // then
        assertThat(lines).extracting(l -> l.item().getName(), DiscountedLine::unitPrice)
                .containsExactly(tuple("Podkładka", 0.01));
    }

    @Test
    void amountSplitsProportionallyWithRemainderOnLargestLine() {
        // given
        DiscountCode code = amount(10);
        BasketItem gpu = product("GPU", 100.00, 1);
        BasketItem ram = product("RAM", 50.00, 1);

        // when
        List<DiscountedLine> lines = DiscountCalculator.calculate(code, List.of(ram, gpu), Set::of);

        // then
        assertThat(lines).extracting(l -> l.item().getName(), DiscountedLine::unitPrice)
                .containsExactly(tuple("RAM", 46.67), tuple("GPU", 93.33));
    }

    @Test
    void amountWithQuantityIsUpToValue() {
        // given
        DiscountCode code = amount(10);
        BasketItem fans = product("Wentylator", 100.00, 3);

        // when
        List<DiscountedLine> lines = DiscountCalculator.calculate(code, List.of(fans), Set::of);

        // then
        assertThat(lines).extracting(DiscountedLine::unitPrice).containsExactly(96.67);
    }

    @Test
    void amountIsCappedAtCoveredValue() {
        // given
        DiscountCode code = amount(500);
        BasketItem gpu = product("GPU", 100.00, 1);
        BasketItem ram = product("RAM", 50.00, 1);

        // when
        List<DiscountedLine> lines = DiscountCalculator.calculate(code, List.of(gpu, ram), Set::of);

        // then
        assertThat(lines).extracting(l -> l.item().getName(), DiscountedLine::unitPrice)
                .containsExactly(tuple("GPU", 0.01), tuple("RAM", 0.01));
    }

    @Test
    void onlyCoveredItemsAreDiscounted() {
        // given
        DiscountCode code = amount(10, DiscountTarget.Brand, "Acme");
        BasketItem acme = product("GPU", 100.00, 1);
        acme.setBrand("Acme");
        BasketItem globex = product("RAM", 100.00, 1);
        globex.setBrand("Globex");
        BasketItem service = product("Montaż", 100.00, 1);
        service.setBrand("Acme");
        service.setService(true);

        // when
        List<DiscountedLine> lines = DiscountCalculator.calculate(code, List.of(acme, globex, service), Set::of);

        // then
        assertThat(lines).extracting(l -> l.item().getName(), DiscountedLine::unitPrice)
                .containsExactly(tuple("GPU", 90.00));
    }

    @Test
    void emptyWhenNothingCovered() {
        // given
        DiscountCode code = percentage(10, DiscountTarget.Category, "Procesory");
        BasketItem gpu = product("GPU", 100.00, 1);

        // when
        List<DiscountedLine> lines = DiscountCalculator.calculate(code, List.of(gpu), Set::of);

        // then
        assertThat(lines).isEmpty();
    }

    @Test
    void emptyWhenCoveredItemsAreFree() {
        // given
        DiscountCode code = amount(10);
        BasketItem gift = product("Gratis", 0.00, 1);

        // when
        List<DiscountedLine> lines = DiscountCalculator.calculate(code, List.of(gift), Set::of);

        // then
        assertThat(lines).isEmpty();
    }

    private static DiscountCode percentage(double value, DiscountTarget target, String... values) {
        return code(DiscountType.Percentage, value, target, values);
    }

    private static DiscountCode percentage(double value) {
        return code(DiscountType.Percentage, value, null);
    }

    private static DiscountCode amount(double value, DiscountTarget target, String... values) {
        return code(DiscountType.Amount, value, target, values);
    }

    private static DiscountCode amount(double value) {
        return code(DiscountType.Amount, value, null);
    }

    private static DiscountCode code(DiscountType type, double value, DiscountTarget target, String... values) {
        DiscountCode code = new DiscountCode();
        code.setType(type);
        code.setValue(value);
        code.setTarget(target);
        code.setTargetValues(Arrays.stream(values).map(v -> new TargetValue(v, v)).toList());
        return code;
    }

    private static BasketItem product(String name, double unitPrice, long qty) {
        return new BasketItem("pim-" + name, name, "MFN-" + name, "Podzespoły", unitPrice, 0, qty, "catalog-1", 2, false);
    }
}
