package pl.commercelink.web.settings;

import org.junit.jupiter.api.Test;
import pl.commercelink.discounts.DiscountTarget;
import pl.commercelink.discounts.DiscountType;
import pl.commercelink.discounts.Promotion;
import pl.commercelink.discounts.TargetValue;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StorePromotionTemplateTest {

    private static final String PATH = "/dashboard/store/promotions";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 12, 12, 0);

    private static Promotion monitorAndCable() {
        Promotion promotion = new Promotion();
        promotion.setStoreId("store-1");
        promotion.setPromotionId("p-1");
        promotion.setName("Monitor + kabel taniej");
        promotion.setConditionTarget(DiscountTarget.Category);
        promotion.setConditionValues(List.of(new TargetValue("Monitory", "Monitory")));
        promotion.setConditionQuantity(1);
        promotion.setType(DiscountType.Percentage);
        promotion.setValue(20);
        promotion.setTarget(DiscountTarget.Product);
        promotion.setTargetValues(List.of(new TargetValue("0101", "Kabel HDMI 2 m")));
        promotion.setMinOrderValue(1500);
        promotion.setValidFrom(LocalDateTime.of(2026, 11, 1, 0, 0));
        promotion.setValidTo(LocalDateTime.of(2026, 12, 31, 23, 59, 59));
        promotion.setActive(true);
        promotion.setCreatedAt(LocalDateTime.of(2026, 10, 28, 10, 14));
        return promotion;
    }

    private String rendered(Promotion promotion) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("settingsPage", null);
        variables.put("navigation", null);
        variables.put("details", PromotionDetailsView.of(promotion, NOW, PATH));
        variables.put("backHref", PATH);
        variables.put("backLabel", "Promocje");
        return SettingsTemplateRenderer.render("store-promotion", variables);
    }

    @Test
    void theTermsAreShownReadOnly() {
        // when
        String html = rendered(monitorAndCable());

        // then
        assertThat(html).contains(">Monitor + kabel taniej<").contains(">Aktywna<")
                .contains("href=\"/dashboard/store/promotions\"").contains(">Promocje<");
        assertThat(html).contains(">Monitory<").contains(">Kategorie · co najmniej 1 szt.<")
                .contains(">1 500,00 zł<")
                .contains(">20%<").contains("procentowy, od ceny każdej objętej sztuki")
                .contains(">Kabel HDMI 2 m<").contains(">ID: 0101<").contains(">Produkty<")
                .contains("01.11.2026, 00:00 – 31.12.2026, 23:59")
                .contains(">28.10.2026, 10:14<").contains("wygrywa wcześniej utworzona");
        assertThat(html).doesNotContainPattern("<input(?![^>]*type=\"hidden\")").doesNotContain("??");
    }

    @Test
    void aPromotionWithoutConditionOnTheWholeBasketSaysSo() {
        // given
        Promotion promotion = monitorAndCable();
        promotion.setConditionTarget(null);
        promotion.setConditionValues(List.of());
        promotion.setConditionQuantity(0);
        promotion.setType(DiscountType.Amount);
        promotion.setValue(150);
        promotion.setTarget(null);
        promotion.setTargetValues(List.of());
        promotion.setMinOrderValue(0);
        promotion.setValidFrom(null);
        promotion.setValidTo(null);

        // when
        String html = rendered(promotion);

        // then
        assertThat(html).contains(">Bez warunku<").contains(">Brak<").contains(">150,00 zł<")
                .contains("kwotowy, dzielony na objęte produkty").contains(">Cały koszyk<").contains(">bez terminu<");
    }

    @Test
    void thePromotionCanBeDeletedAfterConfirmation() {
        // when
        String html = rendered(monitorAndCable());

        // then
        assertThat(html).contains("href=\"/dashboard/store/promotions/p-1/delete\"").contains("data-cl-confirm")
                .contains("data-cl-confirm-title=\"Usunąć promocję Monitor + kabel taniej?\"")
                .contains("Zamówienia złożone z promocją zachowają jej nazwę i rabat.")
                .contains("/js/confirm-dialog.js");
    }

    @Test
    void theToggleSaysWhatItWillDo() {
        // given
        Promotion switchedOff = monitorAndCable();
        switchedOff.setActive(false);

        // when
        String active = rendered(monitorAndCable());
        String disabled = rendered(switchedOff);

        // then
        assertThat(active).contains("action=\"/dashboard/store/promotions/p-1/toggle\"").contains(">Wyłącz promocję<")
                .contains("<input type=\"hidden\" name=\"active\" value=\"false\"/>");
        assertThat(disabled).contains(">Włącz promocję<").contains(">Wyłączona<")
                .contains("<input type=\"hidden\" name=\"active\" value=\"true\"/>");
    }
}
