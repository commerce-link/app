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
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import pl.commercelink.documents.DocumentReason;
import pl.commercelink.inventory.deliveries.DeliveredPredicate;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.starter.util.OperationResult;
import pl.commercelink.warehouse.api.Reservation;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
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

    @Mock
    private WarehouseRepository warehouseRepository;
    @Mock
    private WarehouseInternalReservationService warehouseInternalReservationService;
    @Mock
    private WarehouseInternalIssueService warehouseInternalIssueService;
    @Mock
    private DeliveredPredicate deliveredPredicate;
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
        String view = asStore(() -> controller.markAsReserved(List.of("a", "b"), List.of(3, 1), PL, ra));

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
        String view = asStore(() -> controller.markAsReserved(List.of("a", "b"), List.of(1, 1), PL, ra));

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/warehouse?statuses=Delivered");
        assertThat((String) ra.getFlashAttributes().get("settingsErrorMessage"))
                .contains("RTX b").contains("Zarezerwowane").contains("Nic nie zmieniono");
        verifyNoInteractions(warehouseInternalReservationService);
    }

    @Test
    void refusesItemOfAnotherStore() throws Exception {
        // given
        when(warehouseRepository.findById("store-1", "x")).thenReturn(null);
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        // when
        asStore(() -> controller.markAsReserved(List.of("x"), List.of(1), PL, ra));

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
        asStore(() -> controller.markAsReserved(List.of("a"), List.of(3), PL, ra));

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
        asStore(() -> controller.markAsReserved(List.of("a", "b"), List.of(1), PL, ra));

        // then
        assertThat(ra.getFlashAttributes()).containsKey("settingsErrorMessage");
        verifyNoInteractions(warehouseInternalReservationService);
    }

    @Test
    void emptySelectionIsRefused() throws Exception {
        // given
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        // when
        asStore(() -> controller.markAsReserved(null, null, PL, ra));

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
        String view = asStore(() -> controller.markAsInExternalService(List.of("a", "b"), PL, ra));

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
        asStore(() -> controller.markAsDestroyed(List.of("a"), List.of(2), DocumentReason.Theft, "lost", PL, ra));

        // then
        assertThat(ra.getFlashAttributes().get("settingsSavedMessage")).isEqualTo("Zniszczono 2 szt. w 1 poz. (RW).");
    }
}
