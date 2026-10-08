package pl.commercelink.warehouse.builtin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import org.springframework.web.servlet.view.RedirectView;
import pl.commercelink.documents.DocumentReason;
import pl.commercelink.inventory.deliveries.DeliveredPredicate;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.starter.util.OperationResult;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.warehouse.api.Reservation;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static pl.commercelink.orders.FulfilmentStatus.Delivered;
import static pl.commercelink.orders.FulfilmentStatus.InRMA;
import static pl.commercelink.orders.FulfilmentStatus.Reserved;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WarehouseControllerBulkTest {

    private static final Locale PL = Locale.forLanguageTag("pl");
    /** The posted list view of a page opened with no filters: the default status, no search, no category. */
    private static final MultiValueMap<String, String> VIEW = new LinkedMultiValueMap<>();

    @Mock
    private WarehouseRepository warehouseRepository;
    @Mock
    private WarehouseInternalReservationService warehouseInternalReservationService;
    @Mock
    private WarehouseInternalIssueService warehouseInternalIssueService;
    @Mock
    private DeliveredPredicate deliveredPredicate;
    @Mock
    private WarehouseAllocationsManager warehouseAllocationsManager;
    @Mock
    private WarehouseGoodsOutService warehouseGoodsOutService;
    @Mock
    private WarehouseGoodsInService warehouseGoodsInService;
    @Mock
    private StoresRepository storesRepository;
    @Spy
    private ResourceBundleMessageSource messageSource = messages();

    @InjectMocks
    private WarehouseController controller;

    private static ResourceBundleMessageSource messages() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        return messages;
    }

    private WarehouseItem stored(String id, FulfilmentStatus status, int qty) {
        WarehouseItem item = new WarehouseItem("store-1", "d1", "GPU", "RTX " + id, "590", "MFN", 100, qty);
        item.setItemId(id);
        item.setStatus(status);
        when(warehouseRepository.findById("store-1", id)).thenReturn(item);
        return item;
    }

    private static MultiValueMap<String, String> view(String... pairs) {
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        for (int i = 0; i < pairs.length; i += 2) params.add(pairs[i], pairs[i + 1]);
        return params;
    }

    private <T> T asStore(Callable<T> call) throws Exception {
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn("store-1");
            security.when(CustomSecurityContext::getLoggedInUserName).thenReturn("operator");
            return call.call();
        }
    }

    @Test
    void reserveSucceedsWithCountsInTheMessage() throws Exception {
        // given
        stored("a", Delivered, 3);
        stored("b", Delivered, 2);
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        // when
        String view = asStore(() -> controller.markAsReserved(List.of("a", "b"), List.of(3, 1), VIEW, PL, ra));

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/warehouse?statuses=Reserved");
        assertThat(ra.getFlashAttributes().get("settingsSavedMessage")).isEqualTo("Zarezerwowano 4 szt. w 2 poz.");
        verify(warehouseInternalReservationService).create(any(Reservation.class));
    }

    @Test
    void refusesWholeActionWhenOneItemHasWrongStatus() throws Exception {
        // given
        stored("a", Delivered, 3);
        stored("b", Reserved, 1);
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        // when
        String view = asStore(() -> controller.markAsReserved(List.of("a", "b"), List.of(1, 1), VIEW, PL, ra));

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/warehouse");
        assertThat((String) ra.getFlashAttributes().get("settingsErrorMessage"))
                .contains("RTX b").contains("Zarezerwowane").contains("Nic nie zmieniono");
        verifyNoInteractions(warehouseInternalReservationService);
    }

    @Test
    void refusesAnItemDestroyedSinceTheListWasOpenedWithAMessage() throws Exception {
        // given
        stored("a", FulfilmentStatus.Destroyed, 1);
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        // when
        String view = asStore(() -> controller.markAsReserved(List.of("a"), List.of(1), VIEW, PL, ra));

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/warehouse");
        assertThat((String) ra.getFlashAttributes().get("settingsErrorMessage"))
                .contains("RTX a").contains("Zniszczone").contains("Nic nie zmieniono");
        verifyNoInteractions(warehouseInternalReservationService);
    }

    @Test
    void everyFulfilmentStatusHasAWarehouseLabelInBothLanguages() {
        // given
        ResourceBundleMessageSource messages = messages();

        // when / then
        for (FulfilmentStatus status : FulfilmentStatus.values()) {
            for (Locale locale : List.of(PL, Locale.ENGLISH)) {
                assertThat(messages.getMessage(WarehouseStatuses.labelKey(status), null, null, locale))
                        .as("%s in %s", status, locale).isNotBlank();
            }
        }
    }

    @Test
    void refusesItemOfAnotherStore() throws Exception {
        // given
        when(warehouseRepository.findById("store-1", "x")).thenReturn(null);
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        // when
        asStore(() -> controller.markAsReserved(List.of("x"), List.of(1), VIEW, PL, ra));

        // then
        assertThat(ra.getFlashAttributes().get("settingsErrorMessage"))
                .isEqualTo("Nie znaleziono pozycji — odśwież listę. Nic nie zmieniono.");
        verifyNoInteractions(warehouseInternalReservationService);
    }

    @Test
    void refusesQuantityAboveItemQty() throws Exception {
        // given
        stored("a", Delivered, 2);
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        // when
        asStore(() -> controller.markAsReserved(List.of("a"), List.of(3), VIEW, PL, ra));

        // then
        assertThat((String) ra.getFlashAttributes().get("settingsErrorMessage")).contains("od 1 do 2");
        verifyNoInteractions(warehouseInternalReservationService);
    }

    @Test
    void refusesMismatchedQuantities() throws Exception {
        // given
        stored("a", Delivered, 2);
        stored("b", Delivered, 2);
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        // when
        asStore(() -> controller.markAsReserved(List.of("a", "b"), List.of(1), VIEW, PL, ra));

        // then
        assertThat(ra.getFlashAttributes().get("settingsErrorMessage"))
                .isEqualTo("Liczba ilości nie zgadza się z zaznaczonymi pozycjami. Odśwież listę. Nic nie zmieniono.");
        verifyNoInteractions(warehouseInternalReservationService);
    }

    @Test
    void emptySelectionIsRefused() throws Exception {
        // given
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        // when
        asStore(() -> controller.markAsReserved(null, null, VIEW, PL, ra));

        // then
        assertThat(ra.getFlashAttributes().get("settingsErrorMessage")).isEqualTo("Zaznacz co najmniej jedną pozycję.");
    }

    @Test
    void externalServiceFromTwoSourcesIsRefusedInPolish() throws Exception {
        // given
        stored("a", InRMA, 1);
        stored("b", InRMA, 1);
        when(deliveredPredicate.isFromSameSource(eq("store-1"), anyList())).thenReturn(false);
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        // when
        String view = asStore(() -> controller.markAsInExternalService(List.of("a", "b"), view("statuses", "InRMA"), PL, ra));

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/warehouse?statuses=InRMA");
        assertThat((String) ra.getFlashAttributes().get("settingsErrorMessage")).startsWith("Zaznaczone pozycje muszą pochodzić");
    }

    @Test
    void destroyReportsUnitsOnSuccess() throws Exception {
        // given
        stored("a", Delivered, 3);
        when(warehouseInternalIssueService.destroyItems(any(), any(), any(), any(), any())).thenReturn(OperationResult.success());
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        // when
        asStore(() -> controller.markAsDestroyed(List.of("a"), List.of(2), "Theft", "lost", VIEW, PL, ra));

        // then
        assertThat(ra.getFlashAttributes().get("settingsSavedMessage")).isEqualTo("Zniszczono 2 szt. w 1 poz. (RW).");
    }

    @Test
    void releaseWithTheSameItemTwiceIsRefusedWithoutTouchingStock() throws Exception {
        // given
        stored("a", Reserved, 2);
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        // when
        String view = asStore(() -> controller.markAsAvailable(List.of("a", "a"), List.of(1, 1), VIEW, PL, ra));

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/warehouse");
        assertThat(ra.getFlashAttributes().get("settingsErrorMessage"))
                .isEqualTo("Ta sama pozycja jest zaznaczona dwa razy. Odśwież listę. Nic nie zmieniono.");
        verifyNoInteractions(warehouseInternalReservationService);
    }

    @Test
    void destroyWithTheSameItemTwiceIsRefusedWithoutAnyDocument() throws Exception {
        // given
        stored("a", Delivered, 3);
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        // when
        asStore(() -> controller.markAsDestroyed(List.of("a", "b", "a"), List.of(1, 1, 1), "Theft", "lost", VIEW, PL, ra));

        // then
        assertThat(ra.getFlashAttributes().get("settingsErrorMessage"))
                .isEqualTo("Ta sama pozycja jest zaznaczona dwa razy. Odśwież listę. Nic nie zmieniono.");
        verifyNoInteractions(warehouseInternalIssueService);
    }

    @Test
    void allocationWithTheSameItemTwiceIsRefusedInEnglish() throws Exception {
        // given
        stored("a", FulfilmentStatus.New, 1);
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        // when
        asStore(() -> controller.markAsInAllocation(List.of("a", "a"), VIEW, Locale.ENGLISH, ra));

        // then
        assertThat(ra.getFlashAttributes().get("settingsErrorMessage"))
                .isEqualTo("The same item is selected twice. Refresh the list. Nothing changed.");
        verifyNoInteractions(warehouseAllocationsManager);
    }

    @Test
    void destroyWithoutReasonIsRefusedWithNothingChanged() throws Exception {
        // given
        stored("a", Delivered, 3);
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        // when
        String view = asStore(() -> controller.markAsDestroyed(List.of("a"), List.of(1), null, "lost", view("statuses", "Delivered", "q", "rtx"), PL, ra));

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/warehouse?q=rtx");
        assertThat(ra.getFlashAttributes().get("settingsErrorMessage"))
                .isEqualTo("Wybierz powód zniszczenia z listy. Nic nie zmieniono.");
        verifyNoInteractions(warehouseInternalIssueService);
    }

    @Test
    void destroyWithReasonOutsideTheDestroyListIsRefused() throws Exception {
        // given
        stored("a", Delivered, 3);
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        // when
        asStore(() -> controller.markAsDestroyed(List.of("a"), List.of(1), DocumentReason.SupplierDelivery.name(), "lost", VIEW, PL, ra));

        // then
        assertThat(ra.getFlashAttributes().get("settingsErrorMessage"))
                .isEqualTo("Wybierz powód zniszczenia z listy. Nic nie zmieniono.");
        verifyNoInteractions(warehouseInternalIssueService);
    }

    @Test
    void destroyWithUnknownReasonIsRefusedInsteadOfBadRequest() throws Exception {
        // given
        stored("a", Delivered, 3);
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        // when
        asStore(() -> controller.markAsDestroyed(List.of("a"), List.of(1), "Garbage", "lost", VIEW, Locale.ENGLISH, ra));

        // then
        assertThat(ra.getFlashAttributes().get("settingsErrorMessage"))
                .isEqualTo("Choose a reason for destroying from the list. Nothing changed.");
        verifyNoInteractions(warehouseInternalIssueService);
    }

    @Test
    void destroyWithBlankNoteIsRefused() throws Exception {
        // given
        stored("a", Delivered, 3);
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        // when
        String view = asStore(() -> controller.markAsDestroyed(List.of("a"), List.of(1), "Destruction", "   ", view("statuses", "Delivered", "q", "rtx"), PL, ra));

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/warehouse?q=rtx");
        assertThat(ra.getFlashAttributes().get("settingsErrorMessage"))
                .isEqualTo("Opisz, co się stało — notatka jest wymagana. Nic nie zmieniono.");
        verifyNoInteractions(warehouseInternalIssueService);
    }

    @Test
    void destroyWithoutNoteIsRefused() throws Exception {
        // given
        stored("a", Delivered, 3);
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        // when
        asStore(() -> controller.markAsDestroyed(List.of("a"), List.of(1), "Destruction", null, VIEW, PL, ra));

        // then
        assertThat(ra.getFlashAttributes()).containsKey("settingsErrorMessage");
        verifyNoInteractions(warehouseInternalIssueService);
    }

    @Test
    void releaseSucceedsAndRedirectsToStock() throws Exception {
        // given
        stored("a", Reserved, 3);
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        // when
        String view = asStore(() -> controller.markAsAvailable(List.of("a"), List.of(2), view("statuses", "Reserved"), PL, ra));

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/warehouse");
        assertThat(ra.getFlashAttributes().get("settingsSavedMessage")).isEqualTo("Przywrócono na stan 2 szt. w 1 poz.");
        verify(warehouseInternalReservationService).remove(any(Reservation.class));
    }

    @Test
    void rmaSucceedsAndRedirectsToClaims() throws Exception {
        // given
        stored("a", Delivered, 1);
        stored("b", Reserved, 2);
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        // when
        String view = asStore(() -> controller.markAsInRMA(List.of("a", "b"), List.of(1, 2), VIEW, PL, ra));

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/warehouse?statuses=InRMA");
        assertThat(ra.getFlashAttributes().get("settingsSavedMessage")).isEqualTo("Zgłoszono do reklamacji 3 szt. w 2 poz.");
        verify(warehouseInternalReservationService).create(any(Reservation.class));
    }

    @Test
    void allocationSucceedsAndRedirectsToAllocation() throws Exception {
        // given
        stored("a", FulfilmentStatus.New, 1);
        stored("b", FulfilmentStatus.New, 1);
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        // when
        String view = asStore(() -> controller.markAsInAllocation(List.of("a", "b"), VIEW, PL, ra));

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/warehouse?statuses=Allocation");
        assertThat(ra.getFlashAttributes().get("settingsSavedMessage")).isEqualTo("Skierowano do alokacji 2 poz.");
        verify(warehouseAllocationsManager).schedule("store-1", List.of("a", "b"));
    }

    @Test
    void externalServiceSucceedsAndRedirectsToExternalService() throws Exception {
        // given
        stored("a", InRMA, 1);
        when(deliveredPredicate.isFromSameSource(eq("store-1"), anyList())).thenReturn(true);
        when(warehouseGoodsOutService.issueGoodsOutForExternalService("store-1", List.of("a"), "operator"))
                .thenReturn(OperationResult.success());
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        // when
        String view = asStore(() -> controller.markAsInExternalService(List.of("a"), VIEW, PL, ra));

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/warehouse?statuses=InExternalService");
        assertThat(ra.getFlashAttributes().get("settingsSavedMessage")).isEqualTo("Wydano do serwisu 1 poz. (WZ).");
    }

    @Test
    void receivedFromExternalServiceSucceedsAndRedirectsToTheClaimsTheItemsReturnTo() throws Exception {
        // given
        stored("a", FulfilmentStatus.InExternalService, 1);
        when(warehouseGoodsInService.receiveFromExternalService("store-1", List.of("a"), "operator"))
                .thenReturn(OperationResult.success());
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        // when
        String view = asStore(() -> controller.markAsReceivedFromExternalService(List.of("a"), view("statuses", "InExternalService"), PL, ra));

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/warehouse?statuses=InRMA");
        assertThat(ra.getFlashAttributes().get("settingsSavedMessage")).isEqualTo("Przyjęto z serwisu 1 poz. (PZ).");
    }

    @Test
    void successKeepsTheSearchAndCategoriesAndChangesOnlyTheStatus() throws Exception {
        // given
        stored("a", Delivered, 3);
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();
        MultiValueMap<String, String> posted = view("statuses", "Delivered", "categories", "GPU", "categories", "CPU",
                "q", "rtx 40", "page", "2");

        // when
        String view = asStore(() -> controller.markAsReserved(List.of("a"), List.of(1), posted, PL, ra));

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/warehouse?statuses=Reserved&categories=GPU&categories=CPU&q=rtx+40");
    }

    @Test
    void refusalReturnsToTheViewTheOperatorWasOn() throws Exception {
        // given
        stored("a", InRMA, 1);
        stored("b", InRMA, 1);
        when(deliveredPredicate.isFromSameSource(eq("store-1"), anyList())).thenReturn(false);
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();
        MultiValueMap<String, String> posted = view("statuses", "InRMA", "statuses", "Delivered", "q", "abc", "page", "2");

        // when
        String view = asStore(() -> controller.markAsInExternalService(List.of("a", "b"), posted, PL, ra));

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/warehouse?statuses=Delivered&statuses=InRMA&q=abc&page=2");
    }

    @Test
    void failedServiceCallReturnsToTheViewTheOperatorWasOn() throws Exception {
        // given
        stored("a", FulfilmentStatus.InExternalService, 1);
        when(warehouseGoodsInService.receiveFromExternalService("store-1", List.of("a"), "operator"))
                .thenReturn(OperationResult.failure("PZ failed"));
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        // when
        String view = asStore(() -> controller.markAsReceivedFromExternalService(List.of("a"),
                view("statuses", "InExternalService", "categories", "GPU"), PL, ra));

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/warehouse?statuses=InExternalService&categories=GPU");
        assertThat(ra.getFlashAttributes().get("settingsErrorMessage")).isEqualTo("PZ failed");
    }

    @Test
    void refusalInAWmsStoreReturnsToTheSameCanonicalAddressAsTheListItself() throws Exception {
        // given
        Store store = mock(Store.class);
        when(storesRepository.findById("store-1")).thenReturn(store);
        when(store.hasIntegration(IntegrationType.WMS_PROVIDER)).thenReturn(true);
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();
        // every status a WMS store lists, posted from the address of its "all" view
        MultiValueMap<String, String> posted = view("statuses", "New", "statuses", "Allocation", "statuses", "Ordered", "q", "rtx");

        // when
        String view = asStore(() -> controller.markAsInAllocation(List.of(), posted, PL, ra));

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/warehouse?statuses=all&q=rtx");
    }

    @Test
    void hostileSearchTextIsEncodedIntoASafeLocationHeader() throws Exception {
        // given
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();
        MultiValueMap<String, String> posted = view("q", "a\r\nSet-Cookie: x=1 //evil.example {id} ${x}");

        // when
        String view = asStore(() -> controller.markAsReserved(List.of(), List.of(), posted, PL, ra));
        RedirectView redirect = new RedirectView(view.substring("redirect:".length()), true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        redirect.render(java.util.Map.of(), new MockHttpServletRequest(), response);

        // then
        String location = response.getHeader("Location");
        assertThat(location).startsWith("/dashboard/warehouse?q=")
                .doesNotContain("\r").doesNotContain("\n").doesNotContain("//evil").doesNotContain("{").doesNotContain("}")
                .doesNotContain(" ")
                .isEqualTo("/dashboard/warehouse?q=a%0D%0ASet-Cookie%3A+x%3D1+%2F%2Fevil.example+%7Bid%7D+%24%7Bx%7D");
    }
}
