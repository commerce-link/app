package pl.commercelink.web.deliveries.details;

import org.junit.jupiter.api.Test;
import pl.commercelink.documents.Document;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.inventory.deliveries.DeliveryOrderStatus;
import pl.commercelink.inventory.deliveries.DeliveryTrackingState;
import pl.commercelink.orders.Payment;
import pl.commercelink.orders.PaymentDirection;
import pl.commercelink.orders.PaymentSource;
import pl.commercelink.orders.event.Event;
import pl.commercelink.orders.event.EventType;
import pl.commercelink.web.dtos.DeliveryTermsForm;
import pl.commercelink.web.orders.OrderFlash;
import pl.commercelink.web.orders.OrderLabels;
import pl.commercelink.web.orders.OrderNotice;

import java.time.LocalDate;
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
        String receivedAfterFailure = render(data(received(own(withStatus(warehouse(), DeliveryOrderStatus.FAILED)))), ADMIN);

        // then
        assertThat(retry).contains("class=\"cl-page-action-form\" method=\"post\" action=\"/dashboard/deliveries/" + DELIVERY_ID + "/purchase/retry\"")
                .contains(">Powtórz zamówienie<");
        assertThat(reconcile).contains("/purchase/reconcile\"").contains(">Sprawdź u dostawcy<");
        assertThat(receivedAfterFailure).doesNotContain("/purchase/retry").doesNotContain(">Powtórz zamówienie<");
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
        String html = renderPage(page, Map.of(OrderFlash.ATTRIBUTE, notice, "termsDialog", TermsDialog.of(warehouse(), page.links())));

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
    void dropshipOffersNoQuantityChangeAndTheApprovalPanelNeverReturns() throws Exception {
        // when
        String dropship = render(data(dropship()), ADMIN);
        String awaiting = render(data(global(withStatus(dropship(), DeliveryOrderStatus.AWAITING_APPROVAL))), ADMIN);
        String sources = String.join("\n", DeliveryDetailsScriptsContractTest.templates().stream()
                .map(path -> { try { return DeliveryDetailsScriptsContractTest.read(path); } catch (Exception e) { throw new IllegalStateException(e); } })
                .toList());

        // then
        assertThat(dropship).doesNotContain("Zmień zamówioną ilość").doesNotContain("id=\"qty-dialog\"");
        assertThat(awaiting).contains(">Czeka na akceptację<");
        assertThat(sources).doesNotContain("deliveries.purchase.submitted.approval").doesNotContain("deliveries.approval.rejectedBy")
                .doesNotContain("supplierRegistry.getPartnerSiteUrl").doesNotContain("pickerScript('deliveryAddressId'");
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

    private static String allocationsForm(String html) {
        int start = html.indexOf("<form id=\"allocationsForm\"");
        return html.substring(start, html.indexOf("</form>", start));
    }

    @Test
    void theSelectionDialogsSitInsideTheAllocationsFormWithEveryFieldOnce() {
        // given
        Delivery target = warehouse();
        target.setDeliveryId("4c1d9e07-0000-0000-0000-000000000000");
        target.setExternalDeliveryId(null);
        target.setEstimatedDeliveryAt(LocalDate.of(2026, 10, 10));
        DeliveryPageData data = new DeliveryPageData(warehouse(), "Manual-Hurt", null, List.of(target), null, List.of(),
                null, Set.of(), null, null, NOW);

        // when
        String form = allocationsForm(render(data, ADMIN));

        // then
        assertThat(DeliveryDetailsTemplates.fieldNameCounts(form)).allSatisfy((name, count) -> assertThat(count).as(name).isEqualTo(1));
        assertThat(occurrences(form, "<form")).isEqualTo(1);
        assertThat(form).contains("id=\"receive-dialog\"").contains("id=\"receive-all-dialog\"").contains("id=\"merge-dialog\"")
                .contains("id=\"split-dialog\"").contains("id=\"remove-dialog\"").doesNotContain("id=\"ship-dialog\"")
                .contains("formaction=\"/dashboard/deliveries/markSelectedAsReceived\"")
                .contains("formaction=\"/dashboard/deliveries/mergeSelectedAllocations\"")
                .contains("formaction=\"/dashboard/deliveries/splitSelectedAllocations\"")
                .contains("formaction=\"/dashboard/deliveries/deleteSelectedAllocations\"")
                .contains(">#4c1d9e07 · bez numeru · termin 10.10.2026<")
                .contains("name=\"targetEstimatedDeliveryAt\" value=\"2026-10-08\"");
    }

    @Test
    void receiveAllListsEveryWaitingDestinationAndWhatHappensNext() {
        // when
        String html = render(data(partlyReceived(warehouse())), ADMIN);

        // then
        assertThat(html).contains(">Odebrać całą dostawę 2f9eb794?<")
                .contains("Odbierzesz wszystko, co jeszcze czeka w tej dostawie (1 z 2 przeznaczeń):")
                .contains("→ Magazyn").contains("dostawa stanie się „Odebrana”");
    }

    @Test
    void withoutJavaScriptTheRequestedDialogIsRenderedOpenWithItsSelection() {
        // when
        String receiveAll = render(data(warehouse(), "receive-all", Set.of()), ADMIN);
        String split = render(data(warehouse(), "split", Set.of(1)), ADMIN);

        // then
        assertThat(receiveAll).containsPattern("id=\"receive-all-dialog\"[^>]*open=\"open\"");
        assertThat(receiveAll).containsPattern("name=\"allocations\\[0\\]\\.selected\"[^>]*checked");
        assertThat(split).containsPattern("id=\"split-dialog\"[^>]*open=\"open\"")
                .containsPattern("<ul class=\"cl-dialog-list\" data-cl-selection-list>\\s*<li><span>Samsung MirageDrive 2TB NVMe</span>");
    }

    @Test
    void theShipmentDialogOffersCourierOrPickupPointAndTheCustomersChoice() {
        // given
        DeliveryPageData data = new DeliveryPageData(dropship(), "AcmeB", null, List.of(), dropshipOrder(),
                List.of("DPD", "InPost"), null, Set.of(), null, null, NOW);

        // when
        String html = render(data, ADMIN);

        // then
        assertThat(html).contains("id=\"ship-dialog\"").contains("value=\"Courier\"")
                .containsPattern("value=\"PickupPoint\"[^>]*checked").doesNotContain("PersonalCollection")
                .containsPattern("<option value=\"DPD\" selected")
                .contains("name=\"shipmentCollectionPointCode\"").contains("value=\"PL12345\"")
                .contains("name=\"shipmentTrackingNo\"").contains("type=\"datetime-local\"").contains("value=\"2026-10-01T12:00\"")
                .contains("formaction=\"/dashboard/deliveries/confirmDropshipShipment\"").contains("id=\"ship-error\"");
    }

    @Test
    void removingFromTheDeliverySaysWhatItDoesInEachSituation() {
        // when
        String regular = render(data(warehouse()), ADMIN);
        String dropship = render(data(dropship()), ADMIN);
        String unknown = render(data(outcomeUnknown(own(warehouse()))), ADMIN);

        // then
        assertThat(regular).contains("Pozycje zamówień wrócą do przydziału.");
        assertThat(dropship).contains("Zlecenia u dostawcy to nie anuluje — zrób to w panelu dostawcy.");
        assertThat(unknown).contains("class=\"cl-alert is-warn\"").contains("Wynik zamówienia u dostawcy jest nieznany");
        assertThat(regular).containsPattern("class=\"cl-button is-danger\" data-cl-dialog-submit")
                .containsPattern("data-cl-dialog-close autofocus");
    }

    @Test
    void theTermsDialogTypesVatAsAPercentageAndShowsErrorsAtTheFields() {
        // given
        DeliveryPageModel page = DeliveryPageModelFactory.build(data(warehouse()), ADMIN);
        DeliveryTermsForm typed = DeliveryTermsForm.of(warehouse());
        typed.setShippingCost("19.9.0");
        TermsDialog dialog = TermsDialog.of(warehouse(), page.links())
                .withErrors(typed, Map.of("shippingCost", "deliveries.details.terms.error.shippingCost"));

        // when
        String html = DeliveryDetailsTemplates.render(page, Map.of("termsDialog", dialog));

        // then
        assertThat(html).contains("id=\"delivery-terms-form\"").contains("data-cl-async").contains("data-cl-dialog-close-on-success=\"true\"")
                .contains("action=\"/dashboard/deliveries/details\"").contains("name=\"vat\"").contains("value=\"23\"")
                .contains("inputmode=\"decimal\"").contains("value=\"19.9.0\"").contains("aria-invalid=\"true\"")
                .contains("id=\"shippingCost-error\"").contains("Koszt wysyłki netto: wpisz kwotę liczbą, np. 149,99.")
                .contains("data-cl-error-summary").contains(">Terminy i koszty dostawy 2f9eb794<")
                .doesNotContain("type=\"number\" name=\"vat\"");
    }

    @Test
    void theCommentDialogPostsOnlyTheComment() {
        // given
        Delivery delivery = warehouse();
        delivery.setComment("Rampa B");

        // when
        String html = render(data(delivery), ADMIN);
        int start = html.indexOf("<form id=\"delivery-comment-form\"");
        String form = html.substring(start, html.indexOf("</form>", start));

        // then
        assertThat(DeliveryDetailsTemplates.fieldNameCounts(form)).containsOnlyKeys("source", "deliveryId", "comment");
        assertThat(form).contains("value=\"comment\"").contains(">Rampa B</textarea>");
    }

    @Test
    void theInvoiceDialogOffersBothWaysWithTheIdFieldAlwaysVisible() {
        // when
        String html = render(data(warehouse()), ADMIN);

        // then
        assertThat(html).contains("id=\"invoice-dialog\"").contains("action=\"/dashboard/deliveries/link-invoices\"")
                .containsPattern("name=\"linkMode\" value=\"byOrder\" checked").contains("name=\"linkMode\" value=\"byId\"")
                .contains("faktur zakupowych z numerem MH-2026/0917").contains("class=\"cl-choice-reveal cl-field\"")
                .contains("id=\"invoiceId-error\"").doesNotContain("alert(");
    }

    @Test
    void purchaseRepairDialogsAppearWhereTheirCardOffersThem() {
        // given
        DeliveryPageData failed = new DeliveryPageData(own(withStatus(warehouse(), DeliveryOrderStatus.FAILED)), "AcmeB", null,
                List.of(), null, List.of(), LocalDate.of(2026, 10, 9), Set.of(), null, null, NOW);

        // when
        String failedHtml = render(failed, ADMIN);
        String unknownHtml = render(data(outcomeUnknown(own(warehouse()))), ADMIN);
        String inTransit = render(data(warehouse()), ADMIN);

        // then
        assertThat(failedHtml).contains("id=\"complete-dialog\"").contains("action=\"/dashboard/deliveries/" + DELIVERY_ID + "/purchase/complete\"")
                .contains("name=\"externalOrderId\"").contains("name=\"estimatedDeliveryAt\" required value=\"2026-10-09\"")
                .doesNotContain("id=\"force-dialog\"");
        assertThat(unknownHtml).contains("id=\"force-dialog\"").contains("/purchase/force\"")
                .contains("Jeśli zamówienie jednak istnieje u dostawcy, zostanie złożone drugie zamówienie.");
        assertThat(inTransit).doesNotContain("id=\"complete-dialog\"").doesNotContain("id=\"force-dialog\"");
    }

    @Test
    void theSuperAdminRejectsWithAnOptionalReason() {
        // when
        String html = render(data(global(withStatus(dropship(), DeliveryOrderStatus.AWAITING_APPROVAL))), SUPER_ADMIN);

        // then
        assertThat(html).contains("id=\"reject-dialog\"").contains("action=\"/dashboard/store/store-1/deliveries/ed2fca8a-073d-47cd-8bd3-2f1cedfdbeb2/reject\"")
                .contains("<textarea class=\"cl-input cl-textarea\" id=\"reject-reason\" name=\"reason\"");
    }

    @Test
    void theQuantityDialogIsFilledForTheProductNamedInTheAddress() {
        // given
        DeliveryPageData data = new DeliveryPageData(warehouse(), "Manual-Hurt", null, List.of(), null, List.of(), null,
                Set.of(), "qty", "MFN-MIRAGE-01", NOW);

        // when
        String html = render(data, ADMIN);

        // then
        assertThat(html).containsPattern("id=\"qty-dialog\"[^>]*open=\"open\"")
                .contains("name=\"mfn\" id=\"qty-mfn\" value=\"MFN-MIRAGE-01\"")
                .contains("action=\"/dashboard/deliveries/updateItemQty\"").contains("min=\"1\"").contains("value=\"2\"")
                .contains("Najmniej: 1 — sztuki zarezerwowane dla zamówień i odebrane.")
                .contains("data-up=\"+{n} szt. — stan magazynowy wzrośnie\"");
    }

    @Test
    void readOnlyViewersGetNoDialogs() {
        // when
        String html = render(data(received(warehouse())), USER);

        // then
        assertThat(html).doesNotContain("id=\"terms-dialog\"").doesNotContain("id=\"qty-dialog\"")
                .doesNotContain("id=\"invoice-dialog\"").doesNotContain("id=\"receive-dialog\"");
    }

    @Test
    void documentsListTheReceiptAndTheInvoiceWithTheirActionsForTheStoreAdmin() {
        // given
        Delivery delivery = received(withGoodsReceipt(warehouse()));
        delivery.addDocument(new Document("inv-1", "FV/ACME/0412", "https://invoices.example/0412", DocumentType.InvoiceVat, LocalDate.of(2026, 9, 25)));
        delivery.setSynced(true);

        // when
        String admin = render(data(delivery), ADMIN);
        String superAdmin = render(data(delivery), SUPER_ADMIN);

        // then
        assertThat(admin).contains(">Dokumenty<").contains("<span class=\"cl-status is-ok\">Faktura</span>")
                .contains("<span class=\"cl-status is-ok\">Zsynchronizowana</span>")
                .contains("href=\"/dashboard/warehouse-documents/details?documentId=pz-1\"").contains(">PZ<")
                .contains("href=\"https://invoices.example/0412\" target=\"_blank\" rel=\"noopener\"").contains("fa-external-link-alt")
                .contains(">Wystawiono 25.09.2026<").contains(">Synchronizuj<")
                .contains("href=\"/dashboard/deliveries/" + DELIVERY_ID + "/confirm/unlink-invoice?invoiceId=inv-1\"")
                .contains("data-cl-confirm-title=\"Odpiąć fakturę FV/ACME/0412?\"").contains("data-cl-confirm-tone=\"primary\"");
        assertThat(superAdmin).contains("href=\"/dashboard/store/store-1/warehouse-documents/details?documentId=pz-1\"")
                .doesNotContain(">Synchronizuj<").doesNotContain("unlink-invoice").doesNotContain("invoice-dialog");
    }

    @Test
    void documentsHaveAnEmptyStateAndTheLinkActionOnlyForTheStoreAdmin() {
        // when
        String admin = render(data(warehouse()), ADMIN);
        String user = render(data(warehouse()), USER);

        // then
        assertThat(admin).contains("<span class=\"cl-status is-neutral\">Bez faktury</span>")
                .contains(">Brak dokumentów. PZ powstanie przy odbiorze, fakturę możesz powiązać.<")
                .contains("data-cl-dialog-open=\"invoice-dialog\"").contains(">Powiąż fakturę<");
        assertThat(user).contains(">Brak dokumentów. PZ powstanie przy odbiorze.<").doesNotContain(">Powiąż fakturę<");
        assertThat(render(data(received(warehouse())), ADMIN)).contains("<span class=\"cl-status is-warn\">Bez faktury</span>");
    }

    @Test
    void paymentsSummariseTheDebtAndListEachPaymentWithItsOwnEditDialog() {
        // given
        Delivery delivery = warehouse();
        delivery.addPayment(new Payment("MH-2026/0917", "mBank", PaymentSource.BankTransfer, PaymentDirection.Outgoing,
                3000, 0, "202610010417", LocalDate.of(2026, 10, 1)));

        // when
        String html = render(data(delivery), ADMIN);

        // then
        assertThat(html).contains(">Płatności<").contains("<span class=\"cl-status is-warn\">Niedopłata 3 253,32 PLN</span>")
                .contains(">Do zapłaty<").contains(">Pozostało<").contains(">15.10.2026 (14 dni)<")
                .contains(">3 000,00 PLN<").contains("· Przelew bankowy").contains("ref. MH-2026/0917")
                .contains("operacja 202610010417").contains("data-cl-dialog-open=\"payment-0-dialog\"")
                .contains("data-cl-dialog-open=\"addPaymentModal\" data-mode=\"delivery\"").contains("id=\"addPaymentModal\"")
                .contains("id=\"payment-0-dialog\"").containsPattern("<option value=\"BankTransfer\"[^>]*selected")
                .contains("name=\"payments[0].amount\"").contains("value=\"3000.00\"").contains("inputmode=\"decimal\"")
                .contains("form=\"payment-0-dialog-remove\"").contains(">Usuń wpłatę<")
                .doesNotContain("paymentsEditModal").doesNotContain("togglePaymentsEditModal").doesNotContain("Option[");
        assertThat(occurrences(html, "/js/money.js")).isEqualTo(1);
    }

    @Test
    void removingOnePaymentPostsTheOthersUnderConsecutiveIndexes() {
        // given
        Delivery delivery = warehouse();
        delivery.addPayment(new Payment("A", "Bank", PaymentSource.BankTransfer, 100, 0));
        delivery.addPayment(new Payment("B", "Bank", PaymentSource.Cash, 200, 0));
        delivery.addPayment(new Payment("C", "Bank", PaymentSource.Card, 300, 0));

        // when
        String html = render(data(delivery), ADMIN);
        int start = html.indexOf("<form id=\"payment-1-dialog-remove\"");
        String remove = html.substring(start, html.indexOf("</form>", start));
        int editStart = html.indexOf("action=\"/dashboard/deliveries/" + DELIVERY_ID + "/updatePayments\"", html.indexOf("id=\"payment-1-dialog\""));
        String edit = html.substring(editStart, html.indexOf("</form>", editStart));

        // then
        assertThat(remove).contains("name=\"payments[0].referenceNo\" value=\"A\"").contains("name=\"payments[1].referenceNo\" value=\"C\"")
                .contains("name=\"payments[1].source\" value=\"Card\"").contains("name=\"payments[1].amount\" value=\"300.00\"")
                .doesNotContain("value=\"B\"").doesNotContain("payments[2]");
        assertThat(DeliveryDetailsTemplates.fieldNameCounts(edit)).allSatisfy((name, count) -> assertThat(count).as(name).isEqualTo(1));
        assertThat(edit).contains("name=\"payments[0].referenceNo\" value=\"A\"").contains("name=\"payments[2].referenceNo\" value=\"C\"")
                .contains("name=\"payments[2].source\" value=\"Card\"").contains("name=\"payments[2].amount\" value=\"300.00\"")
                .contains("id=\"payment-1-dialog-reference\"");
    }

    @Test
    void onlyTheStoreAdminOutsideApprovalEditsPayments() {
        // given
        Delivery delivery = warehouse();
        delivery.addPayment(new Payment("A", "Bank", PaymentSource.BankTransfer, 100, 0));

        // when
        String superAdmin = render(data(delivery), SUPER_ADMIN);
        String user = render(data(delivery), USER);
        String approval = render(data(global(withStatus(delivery, DeliveryOrderStatus.AWAITING_APPROVAL))), ADMIN);

        // then
        for (String html : List.of(superAdmin, user, approval)) {
            assertThat(html).contains(">Płatności<").doesNotContain("id=\"addPaymentModal\"").doesNotContain("payment-0-dialog")
                    .doesNotContain(">Dodaj wpłatę<");
        }
    }

    @Test
    void anUnpaidDeliveryWithoutPaymentsSaysSo() {
        // when
        String html = render(data(warehouse()), ADMIN);

        // then
        assertThat(html).contains("<span class=\"cl-status is-neutral\">Nieopłacona</span>").contains(">Brak wpłat.<");
    }

    @Test
    void thePaymentDescriptionJoinsOnlyThePartsThePaymentHas() {
        // given
        Delivery delivery = warehouse();
        delivery.addPayment(new Payment("REF-2", null, PaymentSource.Cash, PaymentDirection.Outgoing,
                100, 0, null, LocalDate.of(2026, 10, 2)));
        delivery.addPayment(new Payment(null, null, PaymentSource.Card, 50, 0));

        // when
        String html = render(data(delivery), ADMIN);

        // then
        assertThat(html).containsPattern("<p class=\"cl-list-desc\"><span>ref\\. REF-2</span> · <span>z 02\\.10\\.2026</span></p>")
                .doesNotContainPattern("<p class=\"cl-list-desc\">\\s*·").doesNotContainPattern("<p class=\"cl-list-desc\">\\s*</p>");
        assertThat(occurrences(html, "class=\"cl-list-desc\"")).isEqualTo(1);
    }

    @Test
    void anOperationNumberCarriesItsDate() {
        // given
        Delivery delivery = warehouse();
        delivery.addPayment(new Payment(null, "mBank", PaymentSource.BankTransfer, PaymentDirection.Outgoing,
                100, 2.5, "OP-1", LocalDate.of(2026, 10, 1)));

        // when
        String html = render(data(delivery), ADMIN);

        // then
        assertThat(html).contains("<span>mBank</span> · <span>operacja OP-1</span><span> z 01.10.2026</span> · <span>prowizja 2,50 PLN</span>");
    }

    @Test
    void invoiceActionsAreAbsentWhileAwaitingApprovalAndForTheUser() {
        // given
        Delivery delivery = received(warehouse());
        delivery.addDocument(new Document("inv-1", "FV/ACME/0412", "https://invoices.example/0412", DocumentType.InvoiceVat, LocalDate.of(2026, 9, 25)));

        // when
        String approval = render(data(global(withStatus(delivery, DeliveryOrderStatus.AWAITING_APPROVAL))), ADMIN);
        String user = render(data(delivery), USER);

        // then
        for (String html : List.of(approval, user)) {
            assertThat(html).contains(">FV/ACME/0412<").doesNotContain(">Synchronizuj<").doesNotContain("unlink-invoice")
                    .doesNotContain(">Powiąż fakturę<");
        }
    }

    @Test
    void paymentsSayDashForWhatIsOwedWhileTheVatIsUnset() {
        // given
        Delivery delivery = warehouse();
        delivery.setTax(0.0);

        // when
        String html = render(data(delivery), ADMIN);

        // then
        assertThat(html).containsPattern("<dt>Do zapłaty</dt><dd class=\"is-numeric\"\\s*>—</dd>")
                .containsPattern("<dt>Pozostało</dt><dd class=\"is-numeric[^\"]*\"\\s*>—</dd>");
    }

    @Test
    void cardsComeInTheReadingOrderOfTheSpec() {
        // when
        String html = render(data(warehouse()), ADMIN);

        // then
        assertThat(html.indexOf("id=\"pozycje\"")).isLessThan(html.indexOf("id=\"dokumenty\""));
        assertThat(html.indexOf("id=\"dokumenty\"")).isLessThan(html.indexOf("id=\"platnosci\""));
        assertThat(html.indexOf("id=\"platnosci\"")).isLessThan(html.indexOf("id=\"historia\""));
        assertThat(html.indexOf("id=\"historia\"")).isLessThan(html.indexOf("id=\"supplier-title\""));
    }
}
