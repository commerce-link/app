package pl.commercelink.web.settings;

import org.junit.jupiter.api.Test;
import pl.commercelink.discounts.DiscountCode;
import pl.commercelink.discounts.DiscountTarget;
import pl.commercelink.discounts.DiscountType;
import pl.commercelink.discounts.TargetValue;
import pl.commercelink.orders.DiscountedOrderEntry;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.web.dtos.FormNumbers;
import pl.commercelink.web.settings.DiscountCodeDetailsView.Orders;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class StoreDiscountCodeTemplateTest {

    private static final String PATH = "/dashboard/store/discounts";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 24, 12, 0);
    private static final String ORDERS_PATH = "/dashboard/orders";
    private static final String FIRST_ORDER = "c381d457-0000-0000-0000-000000000001";

    private static DiscountCode categories(int usedCount) {
        DiscountCode code = new DiscountCode();
        code.setStoreId("store-1");
        code.setCode("GPU-50");
        code.setName("Rabat na podzespoły");
        code.setType(DiscountType.Amount);
        code.setValue(50);
        code.setTarget(DiscountTarget.Category);
        code.setTargetValues(List.of(new TargetValue("Karty graficzne", "Karty graficzne"),
                new TargetValue("Procesory", "Procesory")));
        code.setPaymentProvider("paynow");
        code.setMinOrderValue(1500);
        code.setMaxUses(100);
        code.setUsedCount(usedCount);
        code.setValidFrom(LocalDateTime.of(2026, 10, 1, 8, 30));
        code.setValidTo(LocalDateTime.of(2026, 12, 31, 23, 59, 59));
        code.setActive(true);
        return code;
    }

    private String rendered(DiscountCode code) {
        return rendered(code, Orders.of(Optional.of(List.of()), null, null, ORDERS_PATH));
    }

    private String rendered(DiscountCode code, Orders orders) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("settingsPage", null);
        variables.put("navigation", null);
        variables.put("details", DiscountCodeDetailsView.of(code, NOW, Map.of("paynow", "PayNow"), PATH, orders));
        variables.put("backHref", PATH);
        variables.put("backLabel", "Kody rabatowe");
        return SettingsTemplateRenderer.render("store-discount-code", variables);
    }

    @Test
    void theTermsAreShownReadOnly() {
        // when
        String html = rendered(categories(6));

        // then
        assertThat(html).contains(">GPU-50<").contains(">Rabat na podzespoły<").contains(">Aktywny<")
                .contains("href=\"/dashboard/store/discounts\"").contains(">Kody rabatowe<");
        assertThat(html).contains(">50,00 zł<").contains("kwotowy, dzielony na objęte produkty")
                .contains(">Karty graficzne<").contains(">Procesory<").contains(">Kategorie<")
                .contains(">PayNow<").contains(">1 500,00 zł<").contains(">6 z 100<")
                .contains("01.10.2026, 08:30 – 31.12.2026, 23:59");
        String terms = html.substring(html.indexOf("id=\"discount-params-title\""), html.indexOf("id=\"discount-orders-title\""));
        assertThat(terms).doesNotContainPattern("<input(?![^>]*type=\"hidden\")");
        assertThat(html).doesNotContain("??");
    }

    @Test
    void anUnusedCodeCanBeDeletedAfterConfirmation() {
        // when
        String html = rendered(categories(0));

        // then
        assertThat(html).contains("href=\"/dashboard/store/discounts/GPU-50/delete\"").contains("data-cl-confirm")
                .contains("data-cl-confirm-title=\"Usunąć kod GPU-50?\"")
                .doesNotContain("Kodu użytego w zamówieniach nie można usunąć");
        assertThat(html).contains("/js/confirm-dialog.js");
    }

    @Test
    void aUsedCodeCannotBeDeletedAndSaysWhy() {
        // when
        String html = rendered(categories(1));

        // then
        assertThat(html).doesNotContain("href=\"/dashboard/store/discounts/GPU-50/delete\"")
                .containsPattern("<button[^>]*disabled[^>]*aria-describedby=\"discount-delete-help\"")
                .contains("Kodu użytego w zamówieniach nie można usunąć");
    }

    @Test
    void theToggleSaysWhatItWillDo() {
        // given
        DiscountCode switchedOff = categories(0);
        switchedOff.setActive(false);

        // when
        String active = rendered(categories(0));
        String disabled = rendered(switchedOff);

        // then
        assertThat(active).contains("action=\"/dashboard/store/discounts/GPU-50/toggle\"").contains(">Wyłącz kod<")
                .contains("<input type=\"hidden\" name=\"active\" value=\"false\"/>");
        assertThat(disabled).contains(">Włącz kod<").contains(">Wyłączony<")
                .contains("<input type=\"hidden\" name=\"active\" value=\"true\"/>");
    }

    @Test
    void aWholeBasketCodeWithoutLimitsSaysSo() {
        // given
        DiscountCode code = categories(3);
        code.setType(DiscountType.Percentage);
        code.setValue(10);
        code.setTarget(null);
        code.setTargetValues(List.of());
        code.setPaymentProvider(null);
        code.setMinOrderValue(0);
        code.setMaxUses(0);
        code.setValidFrom(null);
        code.setValidTo(null);

        // when
        String html = rendered(code);

        // then
        assertThat(html).contains(">10%<").contains("procentowy, od ceny każdej objętej sztuki").contains(">Cały koszyk<")
                .contains(">Każda<").contains(">Brak<").contains(">3, bez limitu<").contains(">bez terminu<");
    }

    @Test
    void theOrdersWithTheCodeAreListedWithTheirSums() {
        // given
        Orders orders = Orders.of(Optional.of(List.of(
                new DiscountedOrderEntry(FIRST_ORDER, LocalDateTime.of(2026, 10, 22, 14, 32), "anna.nowak@example.com", 4289, 50, OrderStatus.New),
                new DiscountedOrderEntry("0a8be266-0000-0000-0000-000000000002", LocalDateTime.of(2026, 10, 2, 8, 41), "jan@example.com", 2649, 50, OrderStatus.Cancelled))),
                null, null, ORDERS_PATH);

        // when
        String html = rendered(categories(2), orders);

        // then
        assertThat(html).contains(">Zamówienia z tym kodem<")
                .contains(">Zamówienia<").contains(">2<")
                .contains(">Udzielony rabat<").contains(">100,00 zł<")
                .contains(">Wartość zamówień<").contains(">" + FormNumbers.format(6938) + " zł<")
                .contains(">Średnia wartość zamówienia<").contains(">" + FormNumbers.format(3469) + " zł<");
        assertThat(html).contains("href=\"/dashboard/orders/" + FIRST_ORDER + "\"").contains(">c381d457<")
                .contains(">22.10.2026, 14:32<").contains(">anna.nowak@example.com<")
                .contains(">" + FormNumbers.format(4289) + " zł<").contains(">50,00 zł<")
                .contains(">Nowe<").contains(">Anulowane<");
        assertThat(html).doesNotContain("??");
    }

    @Test
    void withoutAnOrdersPathTheOrderNumbersAreNoLinks() {
        // given
        Orders orders = Orders.of(Optional.of(List.of(
                new DiscountedOrderEntry(FIRST_ORDER, LocalDateTime.of(2026, 10, 22, 14, 32), "anna.nowak@example.com", 4289, 50, OrderStatus.New))),
                null, null, null);

        // when
        String html = rendered(categories(1), orders);

        // then
        assertThat(html).contains(">c381d457<").doesNotContain("/dashboard/orders/");
    }

    @Test
    void withoutOrdersThereIsNoAverageAndTheListSaysSo() {
        // when
        String html = rendered(categories(0));

        // then
        assertThat(html).contains(">Średnia wartość zamówienia<").contains(">—<")
                .contains("Nikt jeszcze nie złożył zamówienia z tym kodem.");
    }

    @Test
    void theDaysNarrowTheOrdersOnThisPage() {
        // given
        Orders narrowed = Orders.of(Optional.of(List.of()), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), ORDERS_PATH);

        // when
        String all = rendered(categories(0));
        String html = rendered(categories(0), narrowed);

        // then
        assertThat(all).contains("action=\"/dashboard/store/discounts/GPU-50\"").contains(">Cały okres<");
        assertThat(html).contains("name=\"from\" value=\"2026-10-01\"").contains("name=\"to\" value=\"2026-10-31\"")
                .contains(">01.10.2026 – 31.10.2026<")
                .contains("href=\"/dashboard/store/discounts/GPU-50\"")
                .contains("Brak zamówień z tym kodem w wybranym okresie.");
    }

    @Test
    void anIndexStillBeingBuiltSaysTheOrdersComeLater() {
        // when
        String html = rendered(categories(3), Orders.of(Optional.empty(), null, null, ORDERS_PATH));

        // then
        assertThat(html).contains(">Zamówienia z tym kodem<")
                .contains("Lista zamówień z tym kodem jest jeszcze przygotowywana. Odśwież stronę za kilka minut.")
                .doesNotContain(">Udzielony rabat<").doesNotContain("name=\"from\"");
    }

    @Test
    void productTargetsShowTheirIdUnderTheName() {
        // given
        DiscountCode code = categories(0);
        code.setTarget(DiscountTarget.Product);
        code.setTargetValues(List.of(new TargetValue("0101", "Zestaw Gamer RTX 5070")));

        // when
        String html = rendered(code);

        // then
        assertThat(html).contains(">Zestaw Gamer RTX 5070<").contains(">ID: 0101<").contains(">Produkty<");
    }
}
