package pl.commercelink.web;

import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.ui.ConcurrentModel;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import pl.commercelink.products.OrphanedProductCleanupService;
import pl.commercelink.stores.CreateStoreRequest;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreActivationService;
import pl.commercelink.stores.StoreCopyService;
import pl.commercelink.stores.StoreCreationService;
import pl.commercelink.stores.StoreDeletionService;
import pl.commercelink.stores.StoreTrialService;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.stores.TrialPeriod;
import pl.commercelink.web.dtos.StoreCopyForm;
import pl.commercelink.web.dtos.StoreCreateForm;
import pl.commercelink.web.settings.ConfirmAction;
import pl.commercelink.web.settings.SettingsFlash;
import pl.commercelink.web.stores.StoresListPage;
import pl.commercelink.web.stores.StoresListPageFactory;
import pl.commercelink.web.stores.StoresListQuery;
import pl.commercelink.web.stores.StoresNotice;

import java.util.List;
import java.util.Map;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    @Mock private StoresListPageFactory storesListPageFactory;
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
        String view = controller.deleteStore(STORE_ID, null, null, locale, redirectAttributes);

        // then
        assertEquals("redirect:/dashboard/stores", view);
        assertEquals(StoresNotice.bad("Błąd usuwania"), redirectAttributes.getFlashAttributes().get("storesNotice"));
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
        String view = controller.deleteStore(STORE_ID, null, null, locale, redirectAttributes);

        // then
        assertEquals("redirect:/dashboard/stores", view);
        assertEquals(StoresNotice.bad("Błąd usuwania"), redirectAttributes.getFlashAttributes().get("storesNotice"));
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
        String view = controller.deleteStore(STORE_ID, "wrong-id", null, locale, redirectAttributes);

        // then
        assertEquals("redirect:/dashboard/stores", view);
        assertEquals(StoresNotice.bad("Nie pasuje"), redirectAttributes.getFlashAttributes().get("storesNotice"));
        verifyNoInteractions(storeDeletionService);
    }

    @Test
    void deleteRegularStoreProceedsWithMatchingConfirmId() {
        // given
        Store store = new Store();
        store.setStoreId(STORE_ID);
        store.setName("Sklep A");
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(storeDeletionService.deleteStore(STORE_ID, StoreDeletionService.Guard.ANY)).thenReturn(true);
        when(messageSource.getMessage(eq("store.delete.success"), aryEq(new Object[]{"Sklep A"}), eq(locale))).thenReturn("Usunięto");
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        String view = controller.deleteStore(STORE_ID, STORE_ID, null, locale, redirectAttributes);

        // then
        assertEquals("redirect:/dashboard/stores", view);
        assertEquals(StoresNotice.ok("Usunięto"), redirectAttributes.getFlashAttributes().get("storesNotice"));
        verify(storeDeletionService).deleteStore(STORE_ID, StoreDeletionService.Guard.ANY);
    }

    @Test
    void deleteRegularStoreAcceptsConfirmIdWithSurroundingWhitespace() {
        // given
        Store store = new Store();
        store.setStoreId(STORE_ID);
        store.setName("Sklep A");
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(storeDeletionService.deleteStore(STORE_ID, StoreDeletionService.Guard.ANY)).thenReturn(true);
        when(messageSource.getMessage(eq("store.delete.success"), aryEq(new Object[]{"Sklep A"}), eq(locale))).thenReturn("Usunięto");
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        String view = controller.deleteStore(STORE_ID, " " + STORE_ID + "\n", null, locale, redirectAttributes);

        // then
        assertEquals("redirect:/dashboard/stores", view);
        assertEquals(StoresNotice.ok("Usunięto"), redirectAttributes.getFlashAttributes().get("storesNotice"));
        verify(storeDeletionService).deleteStore(STORE_ID, StoreDeletionService.Guard.ANY);
    }

    @Test
    void deleteMissingStoreShowsErrorAndSkipsDeletion() {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(null);
        when(messageSource.getMessage("store.delete.missing", null, locale)).thenReturn("Nie istnieje");
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        String view = controller.deleteStore(STORE_ID, null, null, locale, redirectAttributes);

        // then
        assertEquals("redirect:/dashboard/stores", view);
        assertEquals(StoresNotice.bad("Nie istnieje"), redirectAttributes.getFlashAttributes().get("storesNotice"));
        verifyNoInteractions(storeDeletionService);
    }

    @Test
    void deleteDemoStoreProceedsWithoutConfirmId() {
        // given
        Store store = new Store();
        store.setStoreId(STORE_ID);
        store.setName("Sklep A");
        store.setDemo(new pl.commercelink.stores.DemoStoreMetadata("a@b.pl", "x", "y"));
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(storeDeletionService.deleteStore(STORE_ID, StoreDeletionService.Guard.ANY)).thenReturn(true);
        when(messageSource.getMessage(eq("store.delete.success"), aryEq(new Object[]{"Sklep A"}), eq(locale))).thenReturn("Usunięto");
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        String view = controller.deleteStore(STORE_ID, null, null, locale, redirectAttributes);

        // then
        assertEquals("redirect:/dashboard/stores", view);
        assertEquals(StoresNotice.ok("Usunięto"), redirectAttributes.getFlashAttributes().get("storesNotice"));
        verify(storeDeletionService).deleteStore(STORE_ID, StoreDeletionService.Guard.ANY);
    }

    private static final String NEW_ID = "newid00001";

    private static StoreCreateForm createForm(String id, String name, String apiKey) {
        StoreCreateForm form = StoreCreateForm.withId(id);
        form.setName(name);
        form.setApiKey(apiKey);
        return form;
    }

    private static Store store(String id, String name) {
        Store store = new Store();
        store.setStoreId(id);
        store.setName(name);
        return store;
    }

    @Test
    void createPageHandsOutAFreeIdAndGoesBackToTheList() {
        // given
        when(storeCreationService.freeStoreId()).thenReturn(NEW_ID);
        ConcurrentModel model = new ConcurrentModel();

        // when
        String view = controller.createStorePage("/dashboard/stores?status=trial", model);

        // then
        assertEquals("store-create", view);
        assertEquals(NEW_ID, ((StoreCreateForm) model.getAttribute("form")).getNewStoreId());
        assertEquals("/dashboard/stores?status=trial", model.getAttribute("backHref"));
        assertEquals(Map.of(), model.getAttribute("errors"));
    }

    @Test
    void createsStoreUnderTheFormIdWithTheKeySuffixAndShowsTheOutcomeInTheSettings() {
        // given
        when(storesRepository.findById(NEW_ID)).thenReturn(null);
        when(storeCreationService.createStore(CreateStoreRequest.bare(NEW_ID, "Elektro-Max", "yz1234")))
                .thenReturn(store(NEW_ID, "Elektro-Max"));
        when(messageSource.getMessage(eq("store.create.success"), aryEq(new Object[]{"Elektro-Max"}), eq(locale)))
                .thenReturn("Utworzono");
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        String view = controller.createStore(createForm(NEW_ID, " Elektro-Max ", "full-key-wxyz1234"), null,
                new ConcurrentModel(), locale, redirectAttributes);

        // then
        assertEquals("redirect:/dashboard/store/" + NEW_ID, view);
        assertEquals("Utworzono", redirectAttributes.getFlashAttributes().get(SettingsFlash.SAVED_MESSAGE));
        assertNull(redirectAttributes.getFlashAttributes().get("successMessage"));
    }

    @Test
    void createWithoutIdGetsAFreshOne() {
        // given — a form opened before the deploy has no newStoreId field
        when(storeCreationService.freeStoreId()).thenReturn(NEW_ID);
        when(storeCreationService.createStore(CreateStoreRequest.bare(NEW_ID, "Sklep", null))).thenReturn(store(NEW_ID, "Sklep"));

        // when
        String view = controller.createStore(createForm(null, "Sklep", null), null, new ConcurrentModel(), locale,
                new RedirectAttributesModelMap());

        // then
        assertEquals("redirect:/dashboard/store/" + NEW_ID, view);
    }

    @Test
    void blankNameRendersTheFormWithoutCreating() {
        // given
        ConcurrentModel model = new ConcurrentModel();

        // when
        String view = controller.createStore(createForm(NEW_ID, "  ", "abc"), "/dashboard/stores", model, locale,
                new RedirectAttributesModelMap());

        // then
        assertEquals("store-create", view);
        assertEquals(Map.of("name", "store.create.error.name.required", "apiKey", "store.create.error.apiKey.short"),
                model.getAttribute("errors"));
        assertEquals("abc", ((StoreCreateForm) model.getAttribute("form")).getApiKey());
        assertEquals("/dashboard/stores", model.getAttribute("back"));
        verifyNoInteractions(storeCreationService);
    }

    @Test
    void resentFormLandsOnTheStoreItCreated() {
        // given
        when(storesRepository.findById(NEW_ID)).thenReturn(store(NEW_ID, "Elektro-Max"));
        when(messageSource.getMessage(eq("store.create.duplicate"), aryEq(new Object[]{"Elektro-Max"}), eq(locale)))
                .thenReturn("Już był");
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        String view = controller.createStore(createForm(NEW_ID, "Elektro-Max", null), null, new ConcurrentModel(), locale,
                redirectAttributes);

        // then
        assertEquals("redirect:/dashboard/store/" + NEW_ID, view);
        assertEquals("Już był", redirectAttributes.getFlashAttributes().get(SettingsFlash.SAVED_MESSAGE));
        verify(storeCreationService, never()).createStore(any());
    }

    @Test
    void idOfAnotherStoreRendersTheFormWithAFreshId() {
        // given
        when(storesRepository.findById(NEW_ID)).thenReturn(store(NEW_ID, "Inny sklep"));
        when(storeCreationService.freeStoreId()).thenReturn("fresh00001");
        ConcurrentModel model = new ConcurrentModel();

        // when
        String view = controller.createStore(createForm(NEW_ID, "Elektro-Max", null), null, model, locale,
                new RedirectAttributesModelMap());

        // then
        assertEquals("store-create", view);
        assertEquals("store.create.error.stale", model.getAttribute("formError"));
        assertEquals("fresh00001", ((StoreCreateForm) model.getAttribute("form")).getNewStoreId());
        verify(storeCreationService, never()).createStore(any());
    }

    @Test
    void lostRaceLandsOnTheStoreTheOtherRequestCreated() {
        // given
        when(storesRepository.findById(NEW_ID)).thenReturn(null).thenReturn(store(NEW_ID, "Elektro-Max"));
        when(storeCreationService.createStore(any())).thenThrow(new ConditionalCheckFailedException("taken"));

        // when
        String view = controller.createStore(createForm(NEW_ID, "Elektro-Max", null), null, new ConcurrentModel(), locale,
                new RedirectAttributesModelMap());

        // then
        assertEquals("redirect:/dashboard/store/" + NEW_ID, view);
    }

    @Test
    void failedCreationRendersTheFormWithTheTypedValues() {
        // given
        when(storesRepository.findById(NEW_ID)).thenReturn(null);
        when(storeCreationService.createStore(any())).thenThrow(new IllegalStateException("boom"));
        ConcurrentModel model = new ConcurrentModel();

        // when
        String view = controller.createStore(createForm(NEW_ID, "Elektro-Max", "ab12cd"), null, model, locale,
                new RedirectAttributesModelMap());

        // then
        assertEquals("store-create", view);
        assertEquals("store.create.error", model.getAttribute("formError"));
        assertEquals("Elektro-Max", ((StoreCreateForm) model.getAttribute("form")).getName());
    }

    @Test
    void convertsTrialToFullAccount() {
        // given
        when(storeTrialService.convertToFullAccount(STORE_ID)).thenReturn(true);
        when(messageSource.getMessage("store.trial.convert.success", null, locale)).thenReturn("Pełne konto");
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        String view = controller.convertTrial(STORE_ID, null, locale, redirectAttributes);

        // then
        assertEquals("redirect:/dashboard/stores", view);
        assertEquals(StoresNotice.ok("Pełne konto"), redirectAttributes.getFlashAttributes().get("storesNotice"));
    }

    @Test
    void reportsStoreThatIsNotOnTrialWhenConverting() {
        // given
        when(storeTrialService.convertToFullAccount(STORE_ID)).thenReturn(false);
        when(messageSource.getMessage("store.trial.missing", null, locale)).thenReturn("Brak okresu próbnego");
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        String view = controller.convertTrial(STORE_ID, null, locale, redirectAttributes);

        // then
        assertEquals("redirect:/dashboard/stores", view);
        assertEquals(StoresNotice.bad("Brak okresu próbnego"), redirectAttributes.getFlashAttributes().get("storesNotice"));
    }

    @Test
    void reportsFailedConversion() {
        // given
        when(storeTrialService.convertToFullAccount(STORE_ID)).thenThrow(new RuntimeException("dynamo down"));
        when(messageSource.getMessage("store.trial.error", null, locale)).thenReturn("Błąd");
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        String view = controller.convertTrial(STORE_ID, null, locale, redirectAttributes);

        // then
        assertEquals("redirect:/dashboard/stores", view);
        assertEquals(StoresNotice.bad("Błąd"), redirectAttributes.getFlashAttributes().get("storesNotice"));
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
        controller.deleteStore(STORE_ID, null, null, locale, redirectAttributes);

        // then
        assertEquals(StoresNotice.bad("Nie pasuje"), redirectAttributes.getFlashAttributes().get("storesNotice"));
        verifyNoInteractions(storeDeletionService);
    }

    @Test
    void deactivatesStore() {
        // given
        when(storeActivationService.deactivate(STORE_ID)).thenReturn(StoreActivationService.Outcome.CHANGED);
        when(messageSource.getMessage("store.activity.deactivate.success", null, locale)).thenReturn("Dezaktywowany");
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        String view = controller.deactivateStore(STORE_ID, null, locale, redirectAttributes);

        // then
        assertEquals("redirect:/dashboard/stores", view);
        assertEquals(StoresNotice.ok("Dezaktywowany"), redirectAttributes.getFlashAttributes().get("storesNotice"));
    }

    @Test
    void warnsWhenStoreIsAlreadyInactive() {
        // given
        when(storeActivationService.deactivate(STORE_ID)).thenReturn(StoreActivationService.Outcome.UNCHANGED);
        when(messageSource.getMessage("store.activity.unchanged", null, locale)).thenReturn("Bez zmian");
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        controller.deactivateStore(STORE_ID, null, locale, redirectAttributes);

        // then
        assertEquals(StoresNotice.warn("Bez zmian"), redirectAttributes.getFlashAttributes().get("storesNotice"));
    }

    @Test
    void reportsMissingStoreWhenDeactivating() {
        // given
        when(storeActivationService.deactivate(STORE_ID)).thenReturn(StoreActivationService.Outcome.MISSING);
        when(messageSource.getMessage("store.activity.missing", null, locale)).thenReturn("Brak sklepu");
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        controller.deactivateStore(STORE_ID, null, locale, redirectAttributes);

        // then
        assertEquals(StoresNotice.bad("Brak sklepu"), redirectAttributes.getFlashAttributes().get("storesNotice"));
    }

    @Test
    void reportsFailedDeactivation() {
        // given
        when(storeActivationService.deactivate(STORE_ID)).thenThrow(new RuntimeException("dynamo down"));
        when(messageSource.getMessage("store.activity.error", null, locale)).thenReturn("Błąd");
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        String view = controller.deactivateStore(STORE_ID, null, locale, redirectAttributes);

        // then
        assertEquals("redirect:/dashboard/stores", view);
        assertEquals(StoresNotice.bad("Błąd"), redirectAttributes.getFlashAttributes().get("storesNotice"));
    }

    @Test
    void activatesStore() {
        // given
        when(storeActivationService.activate(STORE_ID)).thenReturn(StoreActivationService.Outcome.CHANGED);
        when(messageSource.getMessage("store.activity.activate.success", null, locale)).thenReturn("Aktywny");
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        String view = controller.activateStore(STORE_ID, null, locale, redirectAttributes);

        // then
        assertEquals("redirect:/dashboard/stores", view);
        assertEquals(StoresNotice.ok("Aktywny"), redirectAttributes.getFlashAttributes().get("storesNotice"));
    }

    @Test
    void sendsStoreWhoseTrialEndedToConversionInsteadOfActivatingIt() {
        // given
        when(storeActivationService.activate(STORE_ID)).thenReturn(StoreActivationService.Outcome.TRIAL_ENDED);
        when(messageSource.getMessage("store.activity.activate.trial-ended", null, locale)).thenReturn("Przenieś na pełne konto");
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        controller.activateStore(STORE_ID, null, locale, redirectAttributes);

        // then
        assertEquals(StoresNotice.bad("Przenieś na pełne konto"), redirectAttributes.getFlashAttributes().get("storesNotice"));
    }

    @Test
    void listDelegatesToFactoryWithParsedQuery() {
        // given
        List<Store> stores = List.of(new Store());
        StoresListQuery query = StoresListQuery.of("trial", "max", "name", "desc", null);
        StoresListPage page = new StoresListPage(query, List.of(), List.of(), null, null);
        when(storesRepository.findAll()).thenReturn(stores);
        when(storesListPageFactory.build(stores, query)).thenReturn(page);
        ConcurrentModel model = new ConcurrentModel();

        // when
        String view = controller.store("trial", "max", "name", "desc", null, model);

        // then
        assertEquals("stores", view);
        assertSame(page, model.getAttribute("page"));
        verify(storesRepository, times(1)).findAll();
        verifyNoMoreInteractions(storesRepository);
    }

    @Test
    void convertConfirmationPageCarriesBackAddress() {
        // given
        Store store = new Store();
        store.setStoreId(STORE_ID);
        store.setName("Sklep A");
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(messageSource.getMessage(eq("stores.list.convert.title"), aryEq(new Object[]{"Sklep A"}), eq(locale))).thenReturn("Przenieść?");
        when(messageSource.getMessage("store.trial.convert.confirm", null, locale)).thenReturn("Treść");
        when(messageSource.getMessage("store.trial.convert.button", null, locale)).thenReturn("Przenieś");
        when(messageSource.getMessage("nav.stores", null, locale)).thenReturn("Sklepy");
        ConcurrentModel model = new ConcurrentModel();

        // when
        String view = controller.confirmConvertTrial(STORE_ID, "/dashboard/stores?status=trial", model, locale);

        // then
        assertEquals("settings-confirm", view);
        assertEquals(new ConfirmAction("Przenieść?", "Treść", "Przenieś",
                "/dashboard/store/" + STORE_ID + "/trial/convert?back=%2Fdashboard%2Fstores%3Fstatus%3Dtrial",
                "/dashboard/stores?status=trial", false), model.getAttribute("confirm"));
        assertEquals("Sklepy", model.getAttribute("backLabel"));
    }

    @Test
    void confirmationPageOfUnknownStoreIsNotFound() {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(null);

        // when / then
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> controller.confirmDeactivate(STORE_ID, null, new ConcurrentModel(), locale));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
    }

    @Test
    void deleteLinkOfRegularStoreOpensTheTypedConfirmationOnTheList() {
        // given
        Store store = new Store();
        store.setStoreId(STORE_ID);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);

        // when
        String view = controller.confirmDelete(STORE_ID, "/dashboard/stores?status=full", new ConcurrentModel(), locale);

        // then
        assertEquals("redirect:/dashboard/stores?status=full&delete=" + STORE_ID, view);
    }

    @Test
    void deleteLinkOfDemoStoreShowsDangerConfirmation() {
        // given
        Store store = new Store();
        store.setStoreId(STORE_ID);
        store.setName("Demo");
        store.setDemo(new pl.commercelink.stores.DemoStoreMetadata("a@b.pl", "x", "y"));
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(messageSource.getMessage(eq("stores.list.delete.title"), aryEq(new Object[]{"Demo"}), eq(locale))).thenReturn("Usunąć?");
        when(messageSource.getMessage("store.delete.confirm", null, locale)).thenReturn("Nieodwracalne");
        when(messageSource.getMessage("stores.list.delete.action", null, locale)).thenReturn("Usuń sklep");
        when(messageSource.getMessage("nav.stores", null, locale)).thenReturn("Sklepy");
        ConcurrentModel model = new ConcurrentModel();

        // when
        String view = controller.confirmDelete(STORE_ID, null, model, locale);

        // then
        assertEquals("settings-confirm", view);
        ConfirmAction confirm = (ConfirmAction) model.getAttribute("confirm");
        assertTrue(confirm.destructive());
        assertEquals("/dashboard/store/" + STORE_ID + "/delete?back=%2Fdashboard%2Fstores", confirm.actionPath());
    }

    @Test
    void cleanupConfirmationPageIsDestructive() {
        // given
        when(messageSource.getMessage("stores.list.cleanup.title", null, locale)).thenReturn("Usunąć osierocone?");
        when(messageSource.getMessage("stores.list.cleanup.message", null, locale)).thenReturn("Treść");
        when(messageSource.getMessage("store.cleanup.button", null, locale)).thenReturn("Usuń osierocone produkty");
        when(messageSource.getMessage("nav.stores", null, locale)).thenReturn("Sklepy");
        ConcurrentModel model = new ConcurrentModel();

        // when
        String view = controller.confirmCleanupProducts(null, model, locale);

        // then
        assertEquals("settings-confirm", view);
        assertTrue(((ConfirmAction) model.getAttribute("confirm")).destructive());
        verifyNoInteractions(orphanedProductCleanupService);
    }

    @Test
    void actionsReturnToTheListStateTheyCameFrom() {
        // given
        when(storeActivationService.deactivate(STORE_ID)).thenReturn(StoreActivationService.Outcome.CHANGED);
        when(messageSource.getMessage("store.activity.deactivate.success", null, locale)).thenReturn("Dezaktywowany");
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        String view = controller.deactivateStore(STORE_ID, "/dashboard/stores?status=trial&q=max", locale, redirectAttributes);

        // then
        assertEquals("redirect:/dashboard/stores?status=trial&q=max", view);
        assertEquals(StoresNotice.ok("Dezaktywowany"), redirectAttributes.getFlashAttributes().get("storesNotice"));
    }

    @Test
    void actionsIgnoreForeignBackAddress() {
        // given
        when(storeActivationService.activate(STORE_ID)).thenReturn(StoreActivationService.Outcome.CHANGED);
        when(messageSource.getMessage("store.activity.activate.success", null, locale)).thenReturn("Aktywny");

        // when
        String view = controller.activateStore(STORE_ID, "https://evil.example", locale, new RedirectAttributesModelMap());

        // then
        assertEquals("redirect:/dashboard/stores", view);
    }

    @Test
    void cleanupReportsDeletedCountAndReturnsToSafeBack() {
        // given
        when(orphanedProductCleanupService.cleanupOrphanedProducts()).thenReturn(7);
        when(messageSource.getMessage(eq("store.cleanup.success"), aryEq(new Object[]{7}), eq(locale))).thenReturn("Usunięto 7");
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        String view = controller.cleanupProducts("/dashboard/stores?status=trial", locale, redirectAttributes);

        // then
        assertEquals("redirect:/dashboard/stores?status=trial", view);
        assertEquals(StoresNotice.ok("Usunięto 7"), redirectAttributes.getFlashAttributes().get("storesNotice"));
    }

    @Test
    void cleanupFailureShowsErrorAndReturnsToTheListWithoutBack() {
        // given
        when(orphanedProductCleanupService.cleanupOrphanedProducts()).thenThrow(new RuntimeException("dynamo down"));
        when(messageSource.getMessage("store.cleanup.error", null, locale)).thenReturn("Błąd czyszczenia");
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        String view = controller.cleanupProducts(null, locale, redirectAttributes);

        // then
        assertEquals("redirect:/dashboard/stores", view);
        assertEquals(StoresNotice.bad("Błąd czyszczenia"), redirectAttributes.getFlashAttributes().get("storesNotice"));
    }

    @Test
    void deleteReturnsToSafeBackAfterSuccess() {
        // given
        Store store = new Store();
        store.setStoreId(STORE_ID);
        store.setName("Sklep A");
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(storeDeletionService.deleteStore(STORE_ID, StoreDeletionService.Guard.ANY)).thenReturn(true);
        when(messageSource.getMessage(eq("store.delete.success"), aryEq(new Object[]{"Sklep A"}), eq(locale))).thenReturn("Usunięto");

        // when
        String view = controller.deleteStore(STORE_ID, STORE_ID, "/dashboard/stores?status=trial", locale, new RedirectAttributesModelMap());

        // then
        assertEquals("redirect:/dashboard/stores?status=trial", view);
    }

    @Test
    void deleteReturnsToSafeBackAfterConfirmationMismatch() {
        // given
        Store store = new Store();
        store.setStoreId(STORE_ID);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(messageSource.getMessage("store.delete.confirm.mismatch", null, locale)).thenReturn("Nie pasuje");
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        String view = controller.deleteStore(STORE_ID, "wrong-id", "/dashboard/stores?status=trial", locale, redirectAttributes);

        // then
        assertEquals("redirect:/dashboard/stores?status=trial", view);
        assertEquals(StoresNotice.bad("Nie pasuje"), redirectAttributes.getFlashAttributes().get("storesNotice"));
        verifyNoInteractions(storeDeletionService);
    }

    @Test
    void deleteSuccessNoticeNamesNamelessStoreByShortId() {
        // given
        Store store = new Store();
        store.setStoreId(STORE_ID);
        store.setName(" ");
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(storeDeletionService.deleteStore(STORE_ID, StoreDeletionService.Guard.ANY)).thenReturn(true);
        when(messageSource.getMessage(eq("store.delete.success"), aryEq(new Object[]{"abc123de"}), eq(locale))).thenReturn("Usunięto abc123de");
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        controller.deleteStore(STORE_ID, STORE_ID, null, locale, redirectAttributes);

        // then
        assertEquals(StoresNotice.ok("Usunięto abc123de"), redirectAttributes.getFlashAttributes().get("storesNotice"));
    }

    @Test
    void convertTrialReturnsToSafeBack() {
        // given
        when(storeTrialService.convertToFullAccount(STORE_ID)).thenReturn(true);
        when(messageSource.getMessage("store.trial.convert.success", null, locale)).thenReturn("Pełne konto");

        // when
        String view = controller.convertTrial(STORE_ID, "/dashboard/stores?status=trial", locale, new RedirectAttributesModelMap());

        // then
        assertEquals("redirect:/dashboard/stores?status=trial", view);
    }

    @Test
    void deactivateConfirmationPageDoesNotDeactivate() {
        // given
        Store store = new Store();
        store.setStoreId(STORE_ID);
        store.setName("Sklep A");
        when(storesRepository.findById(STORE_ID)).thenReturn(store);

        // when
        controller.confirmDeactivate(STORE_ID, null, new ConcurrentModel(), locale);

        // then
        verifyNoInteractions(storeActivationService);
    }

    @Test
    void convertConfirmationPageDoesNotConvert() {
        // given
        Store store = new Store();
        store.setStoreId(STORE_ID);
        store.setName("Sklep A");
        when(storesRepository.findById(STORE_ID)).thenReturn(store);

        // when
        controller.confirmConvertTrial(STORE_ID, null, new ConcurrentModel(), locale);

        // then
        verify(storeTrialService, never()).convertToFullAccount(any());
    }

    private static StoreCopyForm copyForm(String id, String name) {
        return StoreCopyForm.of(name, id);
    }

    @Test
    void copyPageSuggestsANameAndAFreeId() {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(store(STORE_ID, "Madman Gaming"));
        when(storeCreationService.freeStoreId()).thenReturn(NEW_ID);
        when(messageSource.getMessage(eq("store.copy.name.suggested"), aryEq(new Object[]{"Madman Gaming"}), eq(locale)))
                .thenReturn("Madman Gaming (kopia)");
        ConcurrentModel model = new ConcurrentModel();

        // when
        String view = controller.copyStorePage(STORE_ID, null, model, locale);

        // then
        assertEquals("store-copy", view);
        StoreCopyForm form = (StoreCopyForm) model.getAttribute("form");
        assertEquals("Madman Gaming (kopia)", form.getNewStoreName());
        assertEquals(NEW_ID, form.getNewStoreId());
    }

    @Test
    void copyPageBackLeadsToTheSourceSettingsWithoutBack() {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(store(STORE_ID, "Madman Gaming"));
        when(messageSource.getMessage("store.title", null, locale)).thenReturn("Ustawienia sklepu");
        // the page also asks for the suggested name, which these tests do not assert on
        lenient().when(messageSource.getMessage(eq("store.copy.name.suggested"), any(), eq(locale))).thenReturn("suggested");
        ConcurrentModel model = new ConcurrentModel();

        // when
        controller.copyStorePage(STORE_ID, null, model, locale);

        // then
        assertEquals("/dashboard/store/" + STORE_ID, model.getAttribute("backHref"));
        assertEquals("Ustawienia sklepu", model.getAttribute("backLabel"));
        assertNull(model.getAttribute("back"));
    }

    @Test
    void copyPageBackKeepsTheListState() {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(store(STORE_ID, "Madman Gaming"));
        when(messageSource.getMessage("nav.stores", null, locale)).thenReturn("Sklepy");
        // the page also asks for the suggested name, which these tests do not assert on
        lenient().when(messageSource.getMessage(eq("store.copy.name.suggested"), any(), eq(locale))).thenReturn("suggested");
        ConcurrentModel model = new ConcurrentModel();

        // when
        controller.copyStorePage(STORE_ID, "/dashboard/stores?status=trial", model, locale);

        // then
        assertEquals("/dashboard/stores?status=trial", model.getAttribute("backHref"));
        assertEquals("/dashboard/stores?status=trial", model.getAttribute("back"));
        assertEquals("Sklepy", model.getAttribute("backLabel"));
    }

    @Test
    void copyPageIgnoresForeignBack() {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(store(STORE_ID, "Madman Gaming"));
        ConcurrentModel model = new ConcurrentModel();

        // when
        controller.copyStorePage(STORE_ID, "https://evil.example", model, locale);

        // then
        assertEquals("/dashboard/stores", model.getAttribute("backHref"));
    }

    @Test
    void copyPageOfUnknownStoreIsNotFound() {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(null);

        // when / then
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> controller.copyStorePage(STORE_ID, null, new ConcurrentModel(), locale));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
    }

    @Test
    void copiesUnderTheFormIdAndShowsTheOutcomeInTheSettings() {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(store(STORE_ID, "Madman Gaming"));
        when(storesRepository.findById(NEW_ID)).thenReturn(null);
        when(storeCopyService.copyStore(STORE_ID, NEW_ID, "Kopia")).thenReturn(store(NEW_ID, "Kopia"));
        when(messageSource.getMessage(eq("store.copy.success"), aryEq(new Object[]{"Kopia", "Madman Gaming"}), eq(locale)))
                .thenReturn("Skopiowano");
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        String view = controller.copyStore(STORE_ID, copyForm(NEW_ID, " Kopia "), null, new ConcurrentModel(), locale,
                redirectAttributes);

        // then
        assertEquals("redirect:/dashboard/store/" + NEW_ID, view);
        assertEquals("Skopiowano", redirectAttributes.getFlashAttributes().get(SettingsFlash.SAVED_MESSAGE));
    }

    @Test
    void copyWithoutIdGetsAFreshOne() {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(store(STORE_ID, "Madman Gaming"));
        when(storeCreationService.freeStoreId()).thenReturn(NEW_ID);
        when(storeCopyService.copyStore(STORE_ID, NEW_ID, "Kopia")).thenReturn(store(NEW_ID, "Kopia"));

        // when
        String view = controller.copyStore(STORE_ID, copyForm(null, "Kopia"), null, new ConcurrentModel(), locale,
                new RedirectAttributesModelMap());

        // then
        assertEquals("redirect:/dashboard/store/" + NEW_ID, view);
    }

    @Test
    void blankCopyNameRendersTheFormWithoutCopying() {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(store(STORE_ID, "Madman Gaming"));
        ConcurrentModel model = new ConcurrentModel();

        // when
        String view = controller.copyStore(STORE_ID, copyForm(NEW_ID, " "), null, model, locale, new RedirectAttributesModelMap());

        // then
        assertEquals("store-copy", view);
        assertEquals(Map.of("newStoreName", "store.copy.error.name.required"), model.getAttribute("errors"));
        verifyNoInteractions(storeCopyService);
    }

    @Test
    void resentCopyFormLandsOnTheCopyItMade() {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(store(STORE_ID, "Madman Gaming"));
        when(storesRepository.findById(NEW_ID)).thenReturn(store(NEW_ID, "Kopia"));

        // when
        String view = controller.copyStore(STORE_ID, copyForm(NEW_ID, "Kopia"), null, new ConcurrentModel(), locale,
                new RedirectAttributesModelMap());

        // then
        assertEquals("redirect:/dashboard/store/" + NEW_ID, view);
        verifyNoInteractions(storeCopyService);
    }

    @Test
    void failedCopyRendersTheFormWithTheError() {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(store(STORE_ID, "Madman Gaming"));
        when(storesRepository.findById(NEW_ID)).thenReturn(null);
        when(storeCopyService.copyStore(STORE_ID, NEW_ID, "Kopia")).thenThrow(new IllegalStateException("timeout"));
        ConcurrentModel model = new ConcurrentModel();

        // when
        String view = controller.copyStore(STORE_ID, copyForm(NEW_ID, "Kopia"), "/dashboard/stores", model, locale,
                new RedirectAttributesModelMap());

        // then
        assertEquals("store-copy", view);
        assertEquals("store.copy.error", model.getAttribute("formError"));
        assertEquals("/dashboard/stores", model.getAttribute("back"));
    }
}
