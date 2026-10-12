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
    void oldSaveAddressIsTheSameHandler() throws Exception {
        // given: the alias is mapped on the same method, so the mapping annotation carries both paths
        var mapping = WarehouseItemController.class.getDeclaredMethod("saveWarehouseItem", String.class, WarehouseItemEditForm.class,
                org.springframework.ui.Model.class, Locale.class, jakarta.servlet.http.HttpServletResponse.class,
                org.springframework.web.servlet.mvc.support.RedirectAttributes.class)
                .getAnnotation(org.springframework.web.bind.annotation.PostMapping.class);

        // when / then
        assertThat(mapping.value()).containsExactlyInAnyOrder("/dashboard/warehouse/items/{itemId}", "/dashboard/warehouse/items/{itemId}/save");
    }
}
