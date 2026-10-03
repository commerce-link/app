package pl.commercelink.warehouse.builtin;

import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
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
import org.springframework.ui.ConcurrentModel;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import pl.commercelink.inventory.deliveries.DeliveredPredicate;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.starter.security.CustomSecurityContext;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WarehouseShippingGuardTest {

    private static final Locale PL = Locale.forLanguageTag("pl");

    @Mock
    private WarehouseRepository warehouseRepository;
    @Mock
    private DeliveredPredicate deliveredPredicate;
    @Spy
    private ResourceBundleMessageSource messageSource = messages();

    @InjectMocks
    private WarehouseShippingController controller;

    private static ResourceBundleMessageSource messages() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        return messages;
    }

    private WarehouseItem stored(String id, FulfilmentStatus status) {
        WarehouseItem item = new WarehouseItem("store-1", "d1", "GPU", "RTX " + id, "590", "MFN", 100, 1);
        item.setItemId(id);
        item.setStatus(status);
        when(warehouseRepository.findById("store-1", id)).thenReturn(item);
        return item;
    }

    /** The list view the operator acted from, posted by selection-actions.js with the selection. */
    private static MultiValueMap<String, String> listView(String... pairs) {
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        for (int i = 0; i < pairs.length; i += 2) params.add(pairs[i], pairs[i + 1]);
        return params;
    }

    @Test
    void itemsFromTwoSourcesAreSentBackToTheListWithAPolishMessage() {
        // given
        stored("a", FulfilmentStatus.InRMA);
        stored("b", FulfilmentStatus.InRMA);
        when(deliveredPredicate.isFromSameSource(eq("store-1"), anyList())).thenReturn(false);
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        // when
        String view;
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn("store-1");
            view = controller.initiate(List.of("a", "b"), listView("statuses", "InRMA", "categories", "GPU", "q", "rtx"), PL, ra,
                    new ConcurrentModel());
        }

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/warehouse?statuses=InRMA&categories=GPU&q=rtx");
        assertThat((String) ra.getFlashAttributes().get("settingsErrorMessage")).startsWith("Zaznaczone pozycje muszą pochodzić");
    }

    @Test
    void itemsInTheWrongStatusAreRefusedWithTheStatusMessage() {
        // given
        stored("a", FulfilmentStatus.Delivered);
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        // when
        String view;
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn("store-1");
            view = controller.initiate(List.of("a"), listView("statuses", "all", "q", "rtx"), PL, ra, new ConcurrentModel());
        }

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/warehouse?statuses=all&q=rtx");
        assertThat((String) ra.getFlashAttributes().get("settingsErrorMessage")).contains("RTX a").contains("Nic nie zmieniono");
    }
}
