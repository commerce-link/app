package pl.commercelink.web.deliveries.details;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.inventory.deliveries.DeliveryOrderStatus;
import pl.commercelink.inventory.deliveries.DeliveryTrackingState;
import pl.commercelink.orders.event.Event;
import pl.commercelink.orders.event.EventType;
import pl.commercelink.web.orders.OrderFlash;
import pl.commercelink.web.orders.OrderLabels;
import pl.commercelink.web.orders.OrderNotice;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.commercelink.web.deliveries.details.DeliveryDetailsTemplates.occurrences;
import static pl.commercelink.web.deliveries.details.DeliveryDetailsTemplates.render;
import static pl.commercelink.web.deliveries.details.DeliveryDetailsTemplates.renderPage;
import static pl.commercelink.web.deliveries.details.DeliveryFixtures.*;

/** The delivery details page as the controller renders it; replaces the source greps of the old page (spec §16). */
class DeliveryDetailsTemplateTest {

    @Test
    void oneHeadingWithTheShortNumberItsCopyButtonTheStatePillAndTheType() {
        // when
        String html = render(data(warehouse()), ADMIN);

        // then
        assertThat(occurrences(html, "<h1")).isEqualTo(1);
        assertThat(html).contains(">Dostawa 2f9eb794<")
                .contains("data-cl-copy=\"" + DELIVERY_ID + "\"")
                .containsPattern("<span class=\"cl-status is-info\">W drodze</span>")
                .contains("fa-warehouse").contains(">Magazyn<");
    }

    @Test
    void theDropshipTypeStandsNextToThePillNotAmongStatuses() {
        // when
        String html = render(data(dropship()), ADMIN);

        // then
        assertThat(html).contains("fa-truck").contains(">Dropshipping<").contains(">Czeka na wysyłkę<")
                .doesNotContain("deliveries.dropship.orderLink");
    }

    @Test
    void backReturnsToTheFilteredListAndTheSuperAdminToTheQueue() {
        // when
        String admin = render(data(warehouse()), new DeliveryViewer(false, true, "/dashboard/deliveries?scope=all"));
        String superAdmin = render(data(warehouse()), SUPER_ADMIN);

        // then
        assertThat(admin).contains("class=\"cl-back\" href=\"/dashboard/deliveries?scope=all\"").contains(">Dostawy<");
        assertThat(superAdmin).contains(">Kolejka dostaw<");
    }

    @Test
    void theMetaLineNamesTheSupplierTheNumberTheDatesAndTheGrossTotal() {
        // given
        Delivery delivery = partlyReceived(warehouse());
        delivery.setExternalDeliveryIdProvisional(true);

        // when
        String html = render(data(delivery), ADMIN);

        // then
        assertThat(html).contains("<strong>Manual-Hurt</strong>")
                .contains("class=\"cl-meta-truncate\"").contains("title=\"MH-2026/0917\"")
                .contains(">tymczasowy<").contains(">zamówiono 01.10.2026<").contains(">termin 08.10.2026<")
                .contains(">odebrano 1 z 2<").contains("6\u00a0253,32 PLN");
    }

    @Test
    void receiveAllIsThePrimaryActionAndOpensItsDialogWithAFallbackLink() {
        // when
        String html = render(data(warehouse()), ADMIN);

        // then
        assertThat(html).contains("data-cl-dialog-open=\"receive-all-dialog\"").contains("data-cl-select-pending=\"true\"")
                .contains("open=receive-all#receive-all-dialog").contains(">Odbierz całość<");
        assertThat(occurrences(html.substring(0, html.indexOf("cl-layout-aside")), "cl-button is-primary")).isEqualTo(1);
    }

    @Test
    void retryAndReconcilePostWithoutADialog() {
        // when
        String retry = render(data(own(withStatus(warehouse(), DeliveryOrderStatus.FAILED))), ADMIN);
        String reconcile = render(data(outcomeUnknown(own(warehouse()))), ADMIN);

        // then
        assertThat(retry).contains("class=\"cl-page-action-form\" method=\"post\" action=\"/dashboard/deliveries/" + DELIVERY_ID + "/purchase/retry\"")
                .contains(">Powtórz zamówienie<");
        assertThat(reconcile).contains("/purchase/reconcile\"").contains(">Sprawdź u dostawcy<");
    }

    @Test
    void theSuperAdminRealisesAnApprovalAndRejectsItFromTheMenuInThatOrder() {
        // given
        Delivery awaiting = global(withStatus(dropship(), DeliveryOrderStatus.AWAITING_APPROVAL));

        // when
        String html = render(data(awaiting), SUPER_ADMIN);

        // then
        assertThat(html).contains("href=\"/dashboard/store/store-1/deliveries/" + awaiting.getDeliveryId() + "/approval\"")
                .contains(">Zrealizuj u dostawcy<").contains("data-cl-dialog-open=\"reject-dialog\"").contains(">Odrzuć zgłoszenie<")
                .doesNotContain("approval-approve-button").doesNotContain("approval-validation-area");
        assertThat(html.indexOf(">Odrzuć zgłoszenie<")).isLessThan(html.indexOf(">Usuń dostawę<"));
    }

    @Test
    void deleteIsGreyedWithItsReasonAndKeptForAReceivedDelivery() {
        // when
        String inTransit = render(data(warehouse()), ADMIN);
        String received = render(data(received(warehouse())), ADMIN);
        String deletable = render(data(withAllocations(warehouse())), ADMIN);

        // then
        assertThat(inTransit).containsPattern("aria-disabled=\"true\">\\s*<span>Usuń dostawę</span>\\s*<span class=\"cl-menu-reason\">Najpierw usuń pozycje z dostawy.</span>");
        assertThat(received).contains("id=\"delivery-more-menu\"").contains(">Usuń dostawę<");
        assertThat(deletable).contains("href=\"/dashboard/deliveries/" + DELIVERY_ID + "/confirm/delete\"")
                .contains("data-cl-confirm-title=\"Usunąć dostawę 2f9eb794?\"")
                .contains("data-cl-confirm-message=\"Dostawa 2f9eb794 (Manual-Hurt) zostanie usunięta. Tej operacji nie można cofnąć.\"")
                .doesNotContain("confirmSave");
    }

    @Test
    void theUserSeesNoMoreMenuAndNoChangeLinks() {
        // when
        String html = render(data(warehouse()), USER);

        // then
        assertThat(html).doesNotContain("delivery-more-menu").doesNotContain("data-cl-dialog-open=\"terms-dialog\"")
                .doesNotContain("data-cl-dialog-open=\"comment-dialog\"");
    }

    @Test
    void refreshingAProvisionalNumberPostsTheHiddenForm() {
        // given
        Delivery delivery = warehouse();
        delivery.setExternalDeliveryIdProvisional(true);

        // when
        String html = render(data(delivery), ADMIN);

        // then
        assertThat(html).contains("form=\"delivery-refresh-form\"").contains(">Odśwież numer u dostawcy<")
                .contains("id=\"delivery-refresh-form\" method=\"post\" action=\"/dashboard/deliveries/" + DELIVERY_ID + "/refresh-order-id\"");
    }

    @Test
    void aFailedOrderShowsTheSuppliersReasonAndTheManualConfirmation() {
        // given
        Delivery failed = own(withStatus(warehouse(), DeliveryOrderStatus.FAILED));
        failed.setOrderErrorMessage("insufficient stock");

        // when
        String html = render(data(failed), ADMIN);

        // then
        assertThat(html).contains("class=\"cl-card is-status is-bad\"").contains(">Zamówienie u dostawcy nie powiodło się<")
                .contains("Powód od dostawcy:").contains("<code>insufficient stock</code>")
                .contains("data-cl-dialog-open=\"complete-dialog\"").contains(">Potwierdź ręcznie<")
                .doesNotContain("notification is-danger");
    }

    @Test
    void anUnknownOutcomeWarnsNotToRetryBlindlyAndOffersOrderingAnyway() {
        // when
        String html = render(data(outcomeUnknown(own(warehouse()))), ADMIN);

        // then
        assertThat(html).contains("class=\"cl-card is-status is-warn\"").contains("nie ponawiaj na ślepo")
                .contains("<code>HTTP 502 Bad Gateway</code>")
                .containsPattern("class=\"cl-button is-danger-outline\"[^>]*data-cl-dialog-open=\"force-dialog\"");
    }

    @Test
    void terminalTrackingStatesWarnWithoutPromisingAManualCheck() {
        // when
        String cancelled = render(data(tracking(dropship(), DeliveryTrackingState.CANCELLED_BY_SUPPLIER)), ADMIN);
        String noData = render(data(tracking(dropship(), DeliveryTrackingState.SHIPPED_WITHOUT_DATA)), ADMIN);
        String givenUp = render(data(tracking(dropship(), DeliveryTrackingState.GIVEN_UP)), ADMIN);

        // then
        assertThat(cancelled).contains(">Dostawca anulował zlecenie<").contains(">Zaznacz wszystkie i usuń<");
        assertThat(noData).contains(">Dostawca zgłasza wysyłkę bez danych przesyłki<");
        assertThat(givenUp).contains(">Nie udało się pobrać statusu wysyłki od dostawcy<").doesNotContain("sprawdź ponownie");
        for (String html : List.of(cancelled, noData, givenUp)) {
            assertThat(html).doesNotContain("/tracking/check").doesNotContain("tracking-check-form")
                    .doesNotContain("tracking.lastChecked");
        }
    }

    @Test
    void goodsBoundForACustomerAreAnnounced() {
        // given
        Delivery dtc = withAllocations(warehouse(), orderAllocation("SSD", "590", "MFN-SSD", 100, 1, true));

        // when
        String html = render(data(dtc), ADMIN);

        // then
        assertThat(html).contains(">Towar dla klienta przyjdzie do magazynu<");
    }

    @Test
    void theSideColumnDescribesTheOrderTheTermsAndTheComment() {
        // given
        Delivery delivery = warehouse();
        delivery.setComment("Rampa B\nkierowca dzwoni");

        // when
        String html = render(data(delivery), ADMIN);

        // then
        assertThat(html).contains("class=\"cl-layout-side is-grid\"")
                .contains(">Zamówienie u dostawcy<").contains(">Zamówione poza systemem<")
                .contains(">Terminy i koszty<").contains(">14 dni<").contains(">23 %<")
                .contains(">Razem brutto<").contains("data-cl-dialog-open=\"terms-dialog\"")
                .contains("open=terms#terms-dialog").contains("Rampa B\nkierowca dzwoni")
                .doesNotContain(">Odbiorca i wysyłka<");
        assertThat(html.indexOf(">Zamówienie u dostawcy<")).isLessThan(html.indexOf(">Terminy i koszty<"));
    }

    @Test
    void aDropshipDeliveryShowsTheCustomerAndTheShipmentAndThePickupPoint() {
        // given
        DeliveryPageData data = new DeliveryPageData(dropship(), "AcmeB", null, List.of(), dropshipOrder(), List.of("DPD"),
                null, Set.of(), null, null, NOW);

        // when
        String html = render(data, ADMIN);

        // then
        assertThat(html).contains(">Odbiorca i wysyłka<").contains(">Barbara Zając<").contains("href=\"tel:+48 512 345 678\"")
                .contains("href=\"mailto:barbara.zajac@example.com\"").contains(">Punkt odbioru · DPD<").contains(">PL12345<")
                .contains(">Oczekuje na wysyłkę<");
        assertThat(html.indexOf(">Odbiorca i wysyłka<")).isLessThan(html.indexOf(">Terminy i koszty<"));
    }

    @Test
    void aLongSupplierNumberIsCutInTheMetaLineAndWrapsInTheSideCard() {
        // given
        Delivery delivery = warehouse();
        delivery.setExternalDeliveryId("ACMEB-PO-3f2a9c1e-7b4d-4e8a-9c21-5d6f0a1b2c3d");

        // when
        String html = render(data(delivery), ADMIN);

        // then
        assertThat(html).contains("class=\"cl-kv-wide\"").contains("class=\"cl-kv-text is-break\"")
                .contains("aria-label=\"Kopiuj numer u dostawcy\"");
    }

    @Test
    void historyShowsThreeEventsAndANodeForTheRest() {
        // given
        Delivery delivery = warehouse();
        for (int i = 1; i <= 4; i++) {
            delivery.addEvent(new Event(EventType.action, "DELIVERY_UPDATED", LocalDateTime.of(2026, 10, 2, 9, i)));
        }

        // when
        String html = render(data(delivery), ADMIN);
        Delivery none = warehouse();
        none.setEvents(new java.util.LinkedList<>());

        // then
        assertThat(html).contains("data-cl-timeline-limit=\"3\"").contains(">Pokaż wcześniejsze: 2<")
                .contains(">Zmieniono warunki dostawy<").contains(">Utworzona<");
        assertThat(render(data(none), ADMIN)).doesNotContain("id=\"historia\"");
    }

    @Test
    void aBareDeliveryRendersWithoutErrors() {
        // given
        Delivery bare = new Delivery();
        bare.setStoreId(STORE_ID);
        bare.setDeliveryId("d-bare");
        bare.setProvider("Other");

        // when
        String html = render(data(bare), ADMIN);

        // then
        assertThat(html).contains(">Dostawa d-bare<").contains(">Brak komentarza.<").doesNotContain("??");
    }

    @Test
    void anUnsetVatAndAMissingDeliveryDateLeaveDashesInsteadOfZeros() {
        // given
        Delivery delivery = warehouse();
        delivery.setTax(0.0);
        delivery.setEstimatedDeliveryAt(null);

        // when
        String html = render(data(delivery), ADMIN);

        // then
        assertThat(html).contains("<strong>—</strong> <span>brutto</span>").doesNotContain(">termin ")
                .containsPattern("<dt>VAT</dt><dd\\s*>—</dd>")
                .containsPattern("<dt>Razem brutto</dt><dd class=\"is-numeric\"><strong\\s*>—</strong>")
                .doesNotContain("null");
    }

    @Test
    void theOutcomeOfAnActionShowsOnceInThePageAndNeverAsTheLayoutsBanner() {
        // given
        DeliveryPageModel page = DeliveryPageModelFactory.build(data(warehouse()), ADMIN);
        OrderNotice notice = new OrderNotice(OrderLabels.OK, "Zapisano warunki dostawy.", null, null);

        // when
        String html = renderPage(page, Map.of(OrderFlash.ATTRIBUTE, notice));

        // then
        assertThat(occurrences(html, "Zapisano warunki dostawy.")).isEqualTo(1);
        assertThat(occurrences(html, "data-cl-saved-alert")).isEqualTo(1);
        assertThat(html).doesNotContain("notification is-success").doesNotContain("notification is-danger");
    }

    @Test
    void oneTableShowsEveryProductWithItsDestinationsAndTheirStates() {
        // when
        String html = render(data(partlyReceived(warehouse())), ADMIN);

        // then
        assertThat(html).contains(">Pozycje (2)<").contains("class=\"cl-table is-compact is-wrap is-allocations\"")
                .contains(">NVIDIA ValueKing RTX Ultra<").contains(">Samsung MirageDrive 2TB NVMe<")
                .contains("✓ 1 z 1").contains(">0 z 2<").contains("2 × 635,00")
                .contains(">Zamówienie #a9f693b8 · marek.pawlak<").contains("href=\"/dashboard/orders/" + ORDER_ID + "\"")
                .contains("href=\"/dashboard/warehouse/items/wh-MFN-MIRAGE-01\"")
                .contains(">✓ Odebrano<").contains(">Czeka<")
                .contains("Towar razem: netto 5\u00a0084,00 PLN · brutto 6\u00a0253,32 PLN")
                .doesNotContain(">Alokacja<").doesNotContain("Zarezerwowano");
    }

    @Test
    void theItemsFooterSaysDashForTheGrossWhileTheVatIsUnset() {
        // given
        Delivery delivery = warehouse();
        delivery.setTax(0.0);

        // when
        String html = render(data(delivery), ADMIN);

        // then
        assertThat(html).contains("Towar razem: netto 5\u00a0084,00 PLN · brutto —").doesNotContain("null PLN");
    }

    @Test
    void codesCopyOnClickAndTheProductMenuGreysTheQuantityChangeWithAReason() {
        // when
        String html = render(data(withGoodsReceipt(warehouse())), ADMIN);

        // then
        assertThat(html).contains("data-cl-copy=\"5900000000002\"").contains("aria-label=\"Kopiuj kod producenta MFN-VALUE-01\"")
                .containsPattern("aria-disabled=\"true\">\\s*<span>Zmień zamówioną ilość</span>\\s*<span class=\"cl-menu-reason\">Dostawa ma powiązane dokumenty.</span>")
                .contains(">Historia dokumentów pozycji<");
    }

    @Test
    void theSelectionRowOffersTheWarehouseActionsAndHidesThemForDropship() {
        // when
        String warehouse = render(data(warehouse()), ADMIN);
        String dropship = render(data(dropship()), ADMIN);

        // then
        assertThat(warehouse).contains("data-cl-selection-bar hidden").contains("data-template=\"Zaznaczono: {k} (z {n})\"")
                .contains("data-cl-dialog-open=\"receive-dialog\"").contains(">Przenieś<")
                .containsPattern("aria-disabled=\"true\">\\s*<span>Do innej dostawy…</span>\\s*<span class=\"cl-menu-reason\">Brak innych nieodebranych dostaw tego dostawcy.</span>")
                .contains("data-cl-dialog-open=\"split-dialog\"").contains("data-cl-dialog-open=\"remove-dialog\"");
        assertThat(dropship).contains("data-cl-dialog-open=\"ship-dialog\"").contains("data-cl-dialog-open=\"remove-dialog\"")
                .doesNotContain("receive-dialog").doesNotContain(">Przenieś<").doesNotContain("split-dialog")
                .doesNotContain("PersonalCollection");
    }

    @Test
    void receivingIsGreyedWhileTheOrderIsBeingPlacedAndAbsentForTheSuperAdmin() {
        // when
        String admin = render(data(withStatus(warehouse(), DeliveryOrderStatus.ORDER_PENDING)), ADMIN);
        String superAdmin = render(data(withStatus(warehouse(), DeliveryOrderStatus.ORDER_PENDING)), SUPER_ADMIN);

        // then
        assertThat(admin).contains("id=\"selection-receive-reason\">Trwa zamawianie u dostawcy.<")
                .contains("aria-describedby=\"selection-receive-reason\"");
        assertThat(superAdmin).doesNotContain("data-cl-select-row").doesNotContain("data-cl-selection-bar");
    }

    @Test
    void aReceivedDeliveryOrAStoreAdminWaitingForApprovalGetsNoCheckboxes() {
        // when
        String received = render(data(received(warehouse())), ADMIN);
        String approval = render(data(global(withStatus(dropship(), DeliveryOrderStatus.AWAITING_APPROVAL))), ADMIN);
        String approvalSuperAdmin = render(data(global(withStatus(dropship(), DeliveryOrderStatus.AWAITING_APPROVAL))), SUPER_ADMIN);

        // then
        assertThat(received).doesNotContain("data-cl-select-row").doesNotContain("data-cl-selection-bar");
        assertThat(approval).doesNotContain("data-cl-select-row");
        assertThat(approvalSuperAdmin).contains("data-cl-select-row").contains("data-cl-dialog-open=\"remove-dialog\"")
                .doesNotContain("data-cl-dialog-open=\"ship-dialog\"");
    }

    @Test
    void withoutJavaScriptTheSelectionPostsToTheConfirmationRoutes() {
        // when
        String html = render(data(warehouse()), ADMIN);

        // then
        assertThat(html).contains("<noscript>")
                .contains("formaction=\"/dashboard/deliveries/" + DELIVERY_ID + "/confirm/receive\"")
                .contains("formaction=\"/dashboard/deliveries/" + DELIVERY_ID + "/confirm/split\"")
                .contains("formaction=\"/dashboard/deliveries/" + DELIVERY_ID + "/confirm/remove-allocations\"")
                .doesNotContain("/confirm/merge\"");
    }

    @Test
    void theAllocationsFormStartsWithADisabledEnterGuardAndCarriesTheDeliveryOnly() {
        // when
        String html = render(data(warehouse()), ADMIN);

        // then
        assertThat(html).containsPattern("<form id=\"allocationsForm\"[^>]*>\\s*<button type=\"submit\" class=\"cl-visually-hidden\" disabled")
                .contains("name=\"deliveryId\" value=\"" + DELIVERY_ID + "\"")
                .doesNotContain("name=\"storeId\"").doesNotContain("name=\"provider\"");
    }

    @Test
    void aDestinationBoundForTheCustomerIsMarkedInItsRow() {
        // given
        Delivery dtc = withAllocations(warehouse(), orderAllocation("SSD", "590", "MFN-SSD", 100, 1, true));

        // when
        String html = render(data(dtc), ADMIN);

        // then
        assertThat(html).contains("<span class=\"cl-status is-info\">Do klienta przez magazyn</span>");
    }

    @Test
    void theSuperAdminReadsDestinationsAsText() {
        // when
        String html = render(data(warehouse()), SUPER_ADMIN);

        // then
        assertThat(html).doesNotContain("href=\"/dashboard/orders/").doesNotContain("href=\"/dashboard/warehouse/items/")
                .doesNotContain("delivery-mfn-history");
    }

    @Test
    void anEmptyDeliverySaysSo() {
        // when
        String html = render(data(withAllocations(warehouse())), ADMIN);

        // then
        assertThat(html).contains(">Dostawa nie ma pozycji.<").doesNotContain("is-allocations");
    }

    @Test
    void theHeaderSelectAllWaitsForJavaScript() {
        // when
        String html = render(data(warehouse()), ADMIN);

        // then
        assertThat(html).containsPattern("<th scope=\"col\" class=\"cl-table-check\">\\s*<label class=\"cl-check-target\"><input class=\"cl-check-input\" type=\"checkbox\" data-cl-select-all hidden");
    }
}
