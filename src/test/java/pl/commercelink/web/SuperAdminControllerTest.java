package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.ui.ConcurrentModel;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import pl.commercelink.products.OrphanedProductCleanupService;
import pl.commercelink.stores.CreateStoreRequest;
import pl.commercelink.stores.DeactivationReason;
import pl.commercelink.stores.DeactivationStatus;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreActivationService;
import pl.commercelink.stores.StoreActivity;
import pl.commercelink.stores.StoreCopyService;
import pl.commercelink.stores.StoreCreationService;
import pl.commercelink.stores.StoreDeletionService;
import pl.commercelink.stores.StoreTrialService;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.stores.TrialPeriod;
import pl.commercelink.stores.TrialStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SuperAdminControllerTest {

    private static final String STORE_ID = "abc123def4";

    @Mock private StoresRepository storesRepository;
    @Mock private MessageSource messageSource;
    @Mock private StoreCopyService storeCopyService;
    @Mock private OrphanedProductCleanupService orphanedProductCleanupService;
    @Mock private StoreDeletionService storeDeletionService;
    @Mock private StoreCreationService storeCreationService;
    @Mock private StoreTrialService storeTrialService;
    @Mock private StoreActivity storeActivity;
    @Mock private StoreActivationService storeActivationService;
    @InjectMocks private SuperAdminController controller;

    private final Locale locale = Locale.forLanguageTag("pl");

    @Test
    void showsErrorWhenCascadeCompletesPartially() {
        // given
        Store store = new Store();
        store.setStoreId(STORE_ID);
        store.setDemo(new pl.commercelink.stores.DemoStoreMetadata("a@b.pl", "x", "y"));
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(storeDeletionService.deleteStore(STORE_ID, StoreDeletionService.Guard.ANY)).thenReturn(false);
        when(messageSource.getMessage("store.delete.error", null, locale)).thenReturn("Błąd usuwania");
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        String view = controller.deleteStore(STORE_ID, null, locale, redirectAttributes);

        // then
        assertEquals("redirect:/dashboard/stores", view);
        assertEquals("Błąd usuwania", redirectAttributes.getFlashAttributes().get("errorMessage"));
        assertNull(redirectAttributes.getFlashAttributes().get("successMessage"));
    }

    @Test
    void showsErrorWhenDeletionThrows() {
        // given
        Store store = new Store();
        store.setStoreId(STORE_ID);
        store.setDemo(new pl.commercelink.stores.DemoStoreMetadata("a@b.pl", "x", "y"));
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(storeDeletionService.deleteStore(STORE_ID, StoreDeletionService.Guard.ANY))
                .thenThrow(new IllegalStateException("not a demo store"));
        when(messageSource.getMessage("store.delete.error", null, locale)).thenReturn("Błąd usuwania");
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        String view = controller.deleteStore(STORE_ID, null, locale, redirectAttributes);

        // then
        assertEquals("redirect:/dashboard/stores", view);
        assertEquals("Błąd usuwania", redirectAttributes.getFlashAttributes().get("errorMessage"));
    }

    @Test
    void deleteRegularStoreRequiresMatchingConfirmId() {
        // given
        Store store = new Store();
        store.setStoreId(STORE_ID);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(messageSource.getMessage("store.delete.confirm.mismatch", null, locale)).thenReturn("Nie pasuje");
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        String view = controller.deleteStore(STORE_ID, "wrong-id", locale, redirectAttributes);

        // then
        assertEquals("redirect:/dashboard/stores", view);
        assertEquals("Nie pasuje", redirectAttributes.getFlashAttributes().get("errorMessage"));
        assertNull(redirectAttributes.getFlashAttributes().get("successMessage"));
        verifyNoInteractions(storeDeletionService);
    }

    @Test
    void deleteRegularStoreProceedsWithMatchingConfirmId() {
        // given
        Store store = new Store();
        store.setStoreId(STORE_ID);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(storeDeletionService.deleteStore(STORE_ID, StoreDeletionService.Guard.ANY)).thenReturn(true);
        when(messageSource.getMessage("store.delete.success", null, locale)).thenReturn("Usunięto");
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        String view = controller.deleteStore(STORE_ID, STORE_ID, locale, redirectAttributes);

        // then
        assertEquals("redirect:/dashboard/stores", view);
        assertEquals("Usunięto", redirectAttributes.getFlashAttributes().get("successMessage"));
        verify(storeDeletionService).deleteStore(STORE_ID, StoreDeletionService.Guard.ANY);
    }

    @Test
    void deleteRegularStoreAcceptsConfirmIdWithSurroundingWhitespace() {
        // given
        Store store = new Store();
        store.setStoreId(STORE_ID);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(storeDeletionService.deleteStore(STORE_ID, StoreDeletionService.Guard.ANY)).thenReturn(true);
        when(messageSource.getMessage("store.delete.success", null, locale)).thenReturn("Usunięto");
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        String view = controller.deleteStore(STORE_ID, " " + STORE_ID + "\n", locale, redirectAttributes);

        // then
        assertEquals("redirect:/dashboard/stores", view);
        assertEquals("Usunięto", redirectAttributes.getFlashAttributes().get("successMessage"));
        verify(storeDeletionService).deleteStore(STORE_ID, StoreDeletionService.Guard.ANY);
    }

    @Test
    void deleteMissingStoreShowsErrorAndSkipsDeletion() {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(null);
        when(messageSource.getMessage("store.delete.missing", null, locale)).thenReturn("Nie istnieje");
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        String view = controller.deleteStore(STORE_ID, null, locale, redirectAttributes);

        // then
        assertEquals("redirect:/dashboard/stores", view);
        assertEquals("Nie istnieje", redirectAttributes.getFlashAttributes().get("errorMessage"));
        assertNull(redirectAttributes.getFlashAttributes().get("successMessage"));
        verifyNoInteractions(storeDeletionService);
    }

    @Test
    void deleteDemoStoreProceedsWithoutConfirmId() {
        // given
        Store store = new Store();
        store.setStoreId(STORE_ID);
        store.setDemo(new pl.commercelink.stores.DemoStoreMetadata("a@b.pl", "x", "y"));
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(storeDeletionService.deleteStore(STORE_ID, StoreDeletionService.Guard.ANY)).thenReturn(true);
        when(messageSource.getMessage("store.delete.success", null, locale)).thenReturn("Usunięto");
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        String view = controller.deleteStore(STORE_ID, null, locale, redirectAttributes);

        // then
        assertEquals("redirect:/dashboard/stores", view);
        assertEquals("Usunięto", redirectAttributes.getFlashAttributes().get("successMessage"));
        verify(storeDeletionService).deleteStore(STORE_ID, StoreDeletionService.Guard.ANY);
    }

    @Test
    void createStoreDelegatesToCreationServiceAndIgnoresClientStoreId() {
        // given
        Store created = new Store();
        created.setStoreId("srv-gen-001");
        when(storeCreationService.createStore(CreateStoreRequest.bare("Nowy sklep", "key-9"))).thenReturn(created);
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        String view = controller.createStore("Nowy sklep", "key-9", locale, redirectAttributes);

        // then
        assertEquals("redirect:/dashboard/store/srv-gen-001", view);
        verify(storeCreationService).createStore(CreateStoreRequest.bare("Nowy sklep", "key-9"));
        verifyNoMoreInteractions(storeCreationService);
    }

    @Test
    void createStoreShowsErrorFlashWhenCreationFails() {
        // given
        when(storeCreationService.createStore(CreateStoreRequest.bare("Nowy sklep", null)))
                .thenThrow(new IllegalStateException("Could not generate a unique store id"));
        when(messageSource.getMessage("store.create.error", null, locale)).thenReturn("Błąd tworzenia");
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        String view = controller.createStore("Nowy sklep", null, locale, redirectAttributes);

        // then
        assertEquals("redirect:/dashboard/store/create", view);
        assertEquals("Błąd tworzenia", redirectAttributes.getFlashAttributes().get("errorMessage"));
        assertNull(redirectAttributes.getFlashAttributes().get("successMessage"));
    }

    private static Store storeWithCreatedAt(String storeId, String createdAt) {
        Store store = new Store();
        store.setStoreId(storeId);
        store.setCreatedAt(createdAt);
        return store;
    }

    @Test
    void sortsStoresNewestFirstByDefaultWithNullsLast() {
        // given
        Store oldest = storeWithCreatedAt("old-store-1", "2026-01-01T10:00:00Z");
        Store newest = storeWithCreatedAt("new-store-1", "2026-07-01T10:00:00Z");
        Store legacy = storeWithCreatedAt("legacy-st-1", null);
        when(storesRepository.findAll()).thenReturn(List.of(oldest, legacy, newest));
        ConcurrentModel model = new ConcurrentModel();

        // when
        String view = controller.store("desc", model);

        // then
        assertEquals("stores", view);
        assertEquals(List.of(newest, oldest, legacy), model.getAttribute("stores"));
        assertEquals("desc", model.getAttribute("dir"));
    }

    @Test
    void sortsStoresOldestFirstWhenAscendingRequested() {
        // given
        Store oldest = storeWithCreatedAt("old-store-1", "2026-01-01T10:00:00Z");
        Store newest = storeWithCreatedAt("new-store-1", "2026-07-01T10:00:00Z");
        Store legacy = storeWithCreatedAt("legacy-st-1", null);
        when(storesRepository.findAll()).thenReturn(List.of(newest, legacy, oldest));
        ConcurrentModel model = new ConcurrentModel();

        // when
        String view = controller.store("asc", model);

        // then
        assertEquals("stores", view);
        assertEquals(List.of(oldest, newest, legacy), model.getAttribute("stores"));
        assertEquals("asc", model.getAttribute("dir"));
    }

    @Test
    void normalizesUnknownSortDirectionToDescending() {
        // given
        Store oldest = storeWithCreatedAt("old-store-1", "2026-01-01T10:00:00Z");
        Store newest = storeWithCreatedAt("new-store-1", "2026-07-01T10:00:00Z");
        when(storesRepository.findAll()).thenReturn(List.of(oldest, newest));
        ConcurrentModel model = new ConcurrentModel();

        // when
        controller.store("bogus", model);

        // then
        assertEquals(List.of(newest, oldest), model.getAttribute("stores"));
        assertEquals("desc", model.getAttribute("dir"));
    }

    @Test
    void exposesTrialStatusesOfListedStores() {
        // given
        Store trialStore = storeWithCreatedAt("trial-st-1", "2026-09-28T10:00:00Z");
        trialStore.setTrial(new TrialPeriod("owner@example.com", "2026-09-28T10:00:00Z", "2026-10-12T10:00:00Z"));
        Store fullStore = storeWithCreatedAt("full-st-01", "2026-01-01T10:00:00Z");
        TrialStatus status = new TrialStatus(LocalDate.parse("2026-10-12"), 14, false);
        when(storesRepository.findAll()).thenReturn(List.of(trialStore, fullStore));
        when(storeTrialService.status(trialStore)).thenReturn(Optional.of(status));
        when(storeTrialService.status(fullStore)).thenReturn(Optional.empty());
        ConcurrentModel model = new ConcurrentModel();

        // when
        controller.store("desc", model);

        // then
        assertEquals(Map.of("trial-st-1", status), model.getAttribute("trials"));
    }

    @Test
    void convertsTrialToFullAccount() {
        // given
        when(storeTrialService.convertToFullAccount(STORE_ID)).thenReturn(true);
        when(messageSource.getMessage("store.trial.convert.success", null, locale)).thenReturn("Pełne konto");
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        String view = controller.convertTrial(STORE_ID, locale, redirectAttributes);

        // then
        assertEquals("redirect:/dashboard/stores", view);
        assertEquals("Pełne konto", redirectAttributes.getFlashAttributes().get("successMessage"));
        assertNull(redirectAttributes.getFlashAttributes().get("errorMessage"));
    }

    @Test
    void reportsStoreThatIsNotOnTrialWhenConverting() {
        // given
        when(storeTrialService.convertToFullAccount(STORE_ID)).thenReturn(false);
        when(messageSource.getMessage("store.trial.missing", null, locale)).thenReturn("Brak okresu próbnego");
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        String view = controller.convertTrial(STORE_ID, locale, redirectAttributes);

        // then
        assertEquals("redirect:/dashboard/stores", view);
        assertEquals("Brak okresu próbnego", redirectAttributes.getFlashAttributes().get("errorMessage"));
        assertNull(redirectAttributes.getFlashAttributes().get("successMessage"));
    }

    @Test
    void reportsFailedConversion() {
        // given
        when(storeTrialService.convertToFullAccount(STORE_ID)).thenThrow(new RuntimeException("dynamo down"));
        when(messageSource.getMessage("store.trial.error", null, locale)).thenReturn("Błąd");
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        String view = controller.convertTrial(STORE_ID, locale, redirectAttributes);

        // then
        assertEquals("redirect:/dashboard/stores", view);
        assertEquals("Błąd", redirectAttributes.getFlashAttributes().get("errorMessage"));
    }

    @Test
    void deleteTrialStoreRequiresMatchingConfirmId() {
        // given
        Store store = new Store();
        store.setStoreId(STORE_ID);
        store.setTrial(new TrialPeriod("owner@example.com", "2026-09-28T10:00:00Z", "2026-10-12T10:00:00Z"));
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(messageSource.getMessage("store.delete.confirm.mismatch", null, locale)).thenReturn("Nie pasuje");
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        controller.deleteStore(STORE_ID, null, locale, redirectAttributes);

        // then
        assertEquals("Nie pasuje", redirectAttributes.getFlashAttributes().get("errorMessage"));
        verifyNoInteractions(storeDeletionService);
    }

    @Test
    void exposesDeactivationsOfListedStores() {
        // given
        Store inactive = storeWithCreatedAt("inactive-01", "2026-09-28T10:00:00Z");
        Store active = storeWithCreatedAt("active-0001", "2026-01-01T10:00:00Z");
        DeactivationStatus status = new DeactivationStatus(DeactivationReason.TRIAL_ENDED,
                LocalDate.parse("2026-10-12"), LocalDate.parse("2026-10-26"), 6);
        when(storesRepository.findAll()).thenReturn(List.of(inactive, active));
        when(storeActivity.status(inactive)).thenReturn(Optional.of(status));
        when(storeActivity.status(active)).thenReturn(Optional.empty());
        ConcurrentModel model = new ConcurrentModel();

        // when
        controller.store("desc", model);

        // then
        assertEquals(Map.of("inactive-01", status), model.getAttribute("deactivations"));
    }

    @Test
    void deactivatesStore() {
        // given
        when(storeActivationService.deactivate(STORE_ID)).thenReturn(StoreActivationService.Outcome.CHANGED);
        when(messageSource.getMessage("store.activity.deactivate.success", null, locale)).thenReturn("Dezaktywowany");
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        String view = controller.deactivateStore(STORE_ID, locale, redirectAttributes);

        // then
        assertEquals("redirect:/dashboard/stores", view);
        assertEquals("Dezaktywowany", redirectAttributes.getFlashAttributes().get("successMessage"));
    }

    @Test
    void warnsWhenStoreIsAlreadyInactive() {
        // given
        when(storeActivationService.deactivate(STORE_ID)).thenReturn(StoreActivationService.Outcome.UNCHANGED);
        when(messageSource.getMessage("store.activity.unchanged", null, locale)).thenReturn("Bez zmian");
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        controller.deactivateStore(STORE_ID, locale, redirectAttributes);

        // then
        assertEquals("Bez zmian", redirectAttributes.getFlashAttributes().get("warningMessage"));
        assertNull(redirectAttributes.getFlashAttributes().get("successMessage"));
    }

    @Test
    void reportsMissingStoreWhenDeactivating() {
        // given
        when(storeActivationService.deactivate(STORE_ID)).thenReturn(StoreActivationService.Outcome.MISSING);
        when(messageSource.getMessage("store.activity.missing", null, locale)).thenReturn("Brak sklepu");
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        controller.deactivateStore(STORE_ID, locale, redirectAttributes);

        // then
        assertEquals("Brak sklepu", redirectAttributes.getFlashAttributes().get("errorMessage"));
    }

    @Test
    void reportsFailedDeactivation() {
        // given
        when(storeActivationService.deactivate(STORE_ID)).thenThrow(new RuntimeException("dynamo down"));
        when(messageSource.getMessage("store.activity.error", null, locale)).thenReturn("Błąd");
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        String view = controller.deactivateStore(STORE_ID, locale, redirectAttributes);

        // then
        assertEquals("redirect:/dashboard/stores", view);
        assertEquals("Błąd", redirectAttributes.getFlashAttributes().get("errorMessage"));
    }

    @Test
    void activatesStore() {
        // given
        when(storeActivationService.activate(STORE_ID)).thenReturn(StoreActivationService.Outcome.CHANGED);
        when(messageSource.getMessage("store.activity.activate.success", null, locale)).thenReturn("Aktywny");
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        String view = controller.activateStore(STORE_ID, locale, redirectAttributes);

        // then
        assertEquals("redirect:/dashboard/stores", view);
        assertEquals("Aktywny", redirectAttributes.getFlashAttributes().get("successMessage"));
    }

    @Test
    void sendsStoreWhoseTrialEndedToConversionInsteadOfActivatingIt() {
        // given
        when(storeActivationService.activate(STORE_ID)).thenReturn(StoreActivationService.Outcome.TRIAL_ENDED);
        when(messageSource.getMessage("store.activity.activate.trial-ended", null, locale)).thenReturn("Przenieś na pełne konto");
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        controller.activateStore(STORE_ID, locale, redirectAttributes);

        // then
        assertEquals("Przenieś na pełne konto", redirectAttributes.getFlashAttributes().get("errorMessage"));
        assertNull(redirectAttributes.getFlashAttributes().get("successMessage"));
    }
}
