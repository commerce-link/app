package pl.commercelink.warehouse.builtin;

import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.starter.security.CustomSecurityContext;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WarehouseItemControllerTest {

    private static final Locale PL = Locale.forLanguageTag("pl");
    private static final String ID = "w-1";

    @Mock private WarehouseRepository repository;
    @Mock private WarehouseItemUpdateService updates;
    @Mock private WarehouseAllocationsManager allocations;
    @Mock private WarehouseItemPageFactory pages;
    @InjectMocks private WarehouseItemController controller;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        ReflectionTestUtils.setField(controller, "messageSource", messages);
        when(updates.update(any(), any(), any())).thenReturn(ID);
    }

    private static WarehouseItem item(FulfilmentStatus status) {
        WarehouseItem item = new WarehouseItem("store-1", "d-1", "Dyski SSD", "Samsung 990", "880", "MZ-1", 689.0, 3);
        item.setItemId(ID);
        item.setStatus(status);
        return item;
    }

    private <T> T asStore(Callable<T> call) throws Exception {
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn("store-1");
            security.when(() -> CustomSecurityContext.hasRole("ADMIN")).thenReturn(false);
            return call.call();
        }
    }

    @Test
    void unknownOrForeignItemIsNotFound() {
        // given
        when(repository.findById("store-1", ID)).thenReturn(null);

        // when / then
        assertThatThrownBy(() -> asStore(() -> controller.showWarehouseItem(ID, null, new ExtendedModelMap(), PL)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
        assertThatThrownBy(() -> asStore(() -> controller.saveWarehouseItem(ID, new WarehouseItemEditForm(), new ExtendedModelMap(),
                PL, new MockHttpServletResponse(), new RedirectAttributesModelMap())))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> asStore(() -> controller.deleteWarehouseItem(ID, null, PL, new RedirectAttributesModelMap())))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(updates, allocations);
    }

    @Test
    void savingADestroyedItemStoresOnlyTheCommentAndGoesBackToTheArchive() throws Exception {
        // given
        WarehouseItem stored = item(FulfilmentStatus.Destroyed);
        when(repository.findById("store-1", ID)).thenReturn(stored);
        WarehouseItemEditForm form = WarehouseItemEditForm.of(stored);
        form.setComment("Flooded");
        form.setName("Forged");
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        // when
        String view = asStore(() -> controller.saveWarehouseItem(ID, form, new ExtendedModelMap(), PL, new MockHttpServletResponse(), ra));

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/warehouse/items/destroyed");
        verify(updates).update(eq("store-1"), same(stored), argThat(u -> "Flooded".equals(u.getComment()) && "Samsung 990".equals(u.getName())));
        assertThat(ra.getFlashAttributes().get("settingsSavedMessage")).isEqualTo("Zapisano pozycję Samsung 990.");
    }

    @Test
    void invalidFormAnswers422WithTheTypedValues() throws Exception {
        // given
        WarehouseItem stored = item(FulfilmentStatus.New);
        when(repository.findById("store-1", ID)).thenReturn(stored);
        WarehouseItemEditForm form = WarehouseItemEditForm.of(stored);
        form.setStatus("Destroyed");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        String view = asStore(() -> controller.saveWarehouseItem(ID, form, new ExtendedModelMap(), PL, response, new RedirectAttributesModelMap()));

        // then
        assertThat(view).isEqualTo("warehouse-item");
        assertThat(response.getStatus()).isEqualTo(422);
        verify(pages).page(same(stored), any(), anyBoolean(), same(form), argThat(e -> e.containsKey("status")), isNull(), eq(PL));
        verifyNoInteractions(updates);
    }

    @Test
    void safeBackIsWhereTheSaveLeads() throws Exception {
        // given
        WarehouseItem stored = item(FulfilmentStatus.Delivered);
        when(repository.findById("store-1", ID)).thenReturn(stored);
        WarehouseItemEditForm form = WarehouseItemEditForm.of(stored);
        form.setBack("/dashboard/warehouse?q=ssd&page=2");

        // when
        String view = asStore(() -> controller.saveWarehouseItem(ID, form, new ExtendedModelMap(), PL, new MockHttpServletResponse(), new RedirectAttributesModelMap()));

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/warehouse?q=ssd&page=2");
    }

    @Test
    void foreignBackNeverBecomesTheSaveRedirect() throws Exception {
        // given
        WarehouseItem stored = item(FulfilmentStatus.Delivered);
        when(repository.findById("store-1", ID)).thenReturn(stored);

        for (String evil : new String[]{"https://evil.example", "//evil.example"}) {
            WarehouseItemEditForm form = WarehouseItemEditForm.of(stored);
            form.setBack(evil);

            // when
            String view = asStore(() -> controller.saveWarehouseItem(ID, form, new ExtendedModelMap(), PL,
                    new MockHttpServletResponse(), new RedirectAttributesModelMap()));

            // then
            assertThat(view).as(evil).isEqualTo("redirect:/dashboard/warehouse?statuses=Delivered");
        }
    }

    @Test
    void mergeLeadsToTheItemThatHoldsTheData() throws Exception {
        // given
        WarehouseItem stored = item(FulfilmentStatus.Delivered);
        when(repository.findById("store-1", ID)).thenReturn(stored);
        when(updates.update(any(), any(), any())).thenReturn("w-2");
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        // when
        String view = asStore(() -> controller.saveWarehouseItem(ID, WarehouseItemEditForm.of(stored), new ExtendedModelMap(), PL, new MockHttpServletResponse(), ra));

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/warehouse/items/w-2");
        assertThat((String) ra.getFlashAttributes().get("settingsSavedMessage")).contains("Połączono");
    }

    @Test
    void concurrentChangeAnswers409AndSavesNothingElse() throws Exception {
        // given
        WarehouseItem stored = item(FulfilmentStatus.Delivered);
        when(repository.findById("store-1", ID)).thenReturn(stored);
        when(updates.update(any(), any(), any())).thenThrow(new ConditionalCheckFailedException("version"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        String view = asStore(() -> controller.saveWarehouseItem(ID, WarehouseItemEditForm.of(stored), new ExtendedModelMap(), PL, response, new RedirectAttributesModelMap()));

        // then
        assertThat(view).isEqualTo("warehouse-item");
        assertThat(response.getStatus()).isEqualTo(409);
        verify(pages).page(any(), any(), anyBoolean(), any(), eq(Map.of()), argThat(m -> m.contains("zmieniono")), eq(PL));
    }

    @Test
    void stalePageVersionAnswers409AndSavesNothing() throws Exception {
        // given
        WarehouseItem stored = item(FulfilmentStatus.Delivered);
        stored.setVersion(5L);
        when(repository.findById("store-1", ID)).thenReturn(stored);
        WarehouseItemEditForm form = WarehouseItemEditForm.of(stored);
        form.setVersion("4");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        String view = asStore(() -> controller.saveWarehouseItem(ID, form, new ExtendedModelMap(), PL, response, new RedirectAttributesModelMap()));

        // then
        assertThat(view).isEqualTo("warehouse-item");
        assertThat(response.getStatus()).isEqualTo(409);
        verify(updates, never()).update(any(), any(), any());
        verify(pages).page(any(), any(), anyBoolean(), same(form), eq(Map.of()), argThat(m -> m.contains("zmieniono")), eq(PL));
    }

    @Test
    void resubmittingTheConflictPageStaysRefusedUntilItIsReloaded() throws Exception {
        // given
        WarehouseItem stored = item(FulfilmentStatus.Delivered);
        stored.setVersion(6L);
        when(repository.findById("store-1", ID)).thenReturn(stored);
        WarehouseItemEditForm form = WarehouseItemEditForm.of(stored);
        form.setVersion("5");

        // when
        asStore(() -> controller.saveWarehouseItem(ID, form, new ExtendedModelMap(), PL, new MockHttpServletResponse(), new RedirectAttributesModelMap()));
        WarehouseItemEditForm shown = captureShownForm();
        MockHttpServletResponse second = new MockHttpServletResponse();
        asStore(() -> controller.saveWarehouseItem(ID, shown, new ExtendedModelMap(), PL, second, new RedirectAttributesModelMap()));

        // then
        assertThat(shown.getVersion()).isEqualTo("5");
        assertThat(second.getStatus()).isEqualTo(409);
        verify(updates, never()).update(any(), any(), any());
    }

    private WarehouseItemEditForm captureShownForm() {
        org.mockito.ArgumentCaptor<WarehouseItemEditForm> captor = org.mockito.ArgumentCaptor.forClass(WarehouseItemEditForm.class);
        verify(pages).page(any(), any(), anyBoolean(), captor.capture(), any(), any(), any());
        return captor.getValue();
    }

    @Test
    void matchingVersionSaves() throws Exception {
        // given
        WarehouseItem stored = item(FulfilmentStatus.Delivered);
        stored.setVersion(5L);
        when(repository.findById("store-1", ID)).thenReturn(stored);
        WarehouseItemEditForm form = WarehouseItemEditForm.of(stored);
        form.setVersion("5");

        // when
        String view = asStore(() -> controller.saveWarehouseItem(ID, form, new ExtendedModelMap(), PL, new MockHttpServletResponse(), new RedirectAttributesModelMap()));

        // then
        assertThat(view).startsWith("redirect:");
        verify(updates).update(any(), any(), any());
    }

    @Test
    void deletingAnItemOutsideNewIsRefused() throws Exception {
        // given
        when(repository.findById("store-1", ID)).thenReturn(item(FulfilmentStatus.Delivered));
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        // when
        String view = asStore(() -> controller.deleteWarehouseItem(ID, null, PL, ra));

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/warehouse/items/w-1");
        assertThat(ra.getFlashAttributes()).containsKey("settingsErrorMessage");
        verifyNoInteractions(allocations);
    }

    @Test
    void deletingANewItemRemovesItAndGoesBack() throws Exception {
        // given
        when(repository.findById("store-1", ID)).thenReturn(item(FulfilmentStatus.New));
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        // when
        String view = asStore(() -> controller.deleteWarehouseItem(ID, "/dashboard/warehouse?statuses=New", PL, ra));

        // then
        verify(allocations).remove("store-1", ID);
        assertThat(view).isEqualTo("redirect:/dashboard/warehouse?statuses=New");
        assertThat(ra.getFlashAttributes().get("settingsSavedMessage")).isEqualTo("Usunięto pozycję Samsung 990.");
    }

    @Test
    void deletingANewItemWithoutBackGoesToTheListOfNewItems() throws Exception {
        // given
        when(repository.findById("store-1", ID)).thenReturn(item(FulfilmentStatus.New));

        // when
        String view = asStore(() -> controller.deleteWarehouseItem(ID, null, PL, new RedirectAttributesModelMap()));

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/warehouse?statuses=New");
    }

    @Test
    void savingWithoutBackGoesToTheListOfTheStoredStatus() throws Exception {
        // given
        WarehouseItem stored = item(FulfilmentStatus.Delivered);
        when(repository.findById("store-1", ID)).thenReturn(stored);

        // when
        String view = asStore(() -> controller.saveWarehouseItem(ID, WarehouseItemEditForm.of(stored), new ExtendedModelMap(), PL,
                new MockHttpServletResponse(), new RedirectAttributesModelMap()));

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/warehouse?statuses=Delivered");
    }

    @Test
    void deleteConfirmPageWithoutJavaScript() throws Exception {
        // given
        when(repository.findById("store-1", ID)).thenReturn(item(FulfilmentStatus.New));
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = asStore(() -> controller.confirmDeleteWarehouseItem(ID, null, model, PL, new RedirectAttributesModelMap()));

        // then
        assertThat(view).isEqualTo("settings-confirm");
        assertThat(model.get("confirm")).isNotNull();
        verifyNoInteractions(allocations);
    }

    @Test
    void bothSaveAddressesAreMapped() throws Exception {
        // given
        Class<?>[] params = {String.class, WarehouseItemEditForm.class, org.springframework.ui.Model.class, Locale.class,
                jakarta.servlet.http.HttpServletResponse.class, org.springframework.web.servlet.mvc.support.RedirectAttributes.class};

        // when
        var current = WarehouseItemController.class.getDeclaredMethod("saveWarehouseItem", params)
                .getAnnotation(org.springframework.web.bind.annotation.PostMapping.class);
        var old = WarehouseItemController.class.getDeclaredMethod("saveFromPageBeforeRedesign", params)
                .getAnnotation(org.springframework.web.bind.annotation.PostMapping.class);

        // then
        assertThat(current.value()).containsExactly("/dashboard/warehouse/items/{itemId}");
        assertThat(old.value()).containsExactly("/dashboard/warehouse/items/{itemId}/save");
    }

    /** Exactly the fields the page before the redesign posts: tax as the multiplier, no version, no back. */
    private static WarehouseItemEditForm oldPageForm(String status, String comment) {
        WarehouseItemEditForm form = new WarehouseItemEditForm();
        form.setCategory("Dyski SSD");
        form.setName("Samsung 990 Pro");
        form.setQty("3");
        form.setCost("689.0");
        form.setUnitSystemCost("0.0");
        form.setTax("1.23");
        form.setEan("880");
        form.setManufacturerCode("MZ-1");
        form.setDeliveryId("d-1");
        form.setSerialNo("");
        form.setStatus(status);
        form.setCondition("Sealed");
        form.setComment(comment);
        return form;
    }

    @Test
    void oldPageSavingANewItemKeepsItsVatRate() throws Exception {
        // given
        WarehouseItem stored = item(FulfilmentStatus.New);
        stored.setTax(1.23);
        when(repository.findById("store-1", ID)).thenReturn(stored);
        org.mockito.ArgumentCaptor<WarehouseItem> saved = org.mockito.ArgumentCaptor.forClass(WarehouseItem.class);

        // when
        String view = asStore(() -> controller.saveFromPageBeforeRedesign(ID, oldPageForm("New", "Old page"), new ExtendedModelMap(), PL,
                new MockHttpServletResponse(), new RedirectAttributesModelMap()));

        // then
        verify(updates).update(eq("store-1"), same(stored), saved.capture());
        assertThat(saved.getValue().getTax()).isCloseTo(1.23, org.assertj.core.data.Offset.offset(1e-4));
        assertThat(saved.getValue().getCost()).isEqualTo(689.0);
        assertThat(saved.getValue().getName()).isEqualTo("Samsung 990 Pro");
        assertThat(view).startsWith("redirect:");
    }

    @Test
    void oldPageSavingADeliveredItemStoresTheAllowedFields() throws Exception {
        // given
        WarehouseItem stored = item(FulfilmentStatus.Delivered);
        stored.setTax(1.23);
        when(repository.findById("store-1", ID)).thenReturn(stored);
        org.mockito.ArgumentCaptor<WarehouseItem> saved = org.mockito.ArgumentCaptor.forClass(WarehouseItem.class);

        // when
        asStore(() -> controller.saveFromPageBeforeRedesign(ID, oldPageForm("Delivered", "Old page"), new ExtendedModelMap(), PL,
                new MockHttpServletResponse(), new RedirectAttributesModelMap()));

        // then
        verify(updates).update(eq("store-1"), same(stored), saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("Samsung 990 Pro");
        assertThat(saved.getValue().getComment()).isEqualTo("Old page");
        assertThat(saved.getValue().getTax()).isEqualTo(1.23);
        assertThat(saved.getValue().getCost()).isEqualTo(689.0);
    }
}
