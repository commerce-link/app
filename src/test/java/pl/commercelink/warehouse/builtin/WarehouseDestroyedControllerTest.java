package pl.commercelink.warehouse.builtin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.util.LinkedMultiValueMap;
import pl.commercelink.starter.security.CustomSecurityContext;

import java.time.LocalDate;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WarehouseDestroyedControllerTest {

    @Mock private DestroyedListService service;
    @InjectMocks private WarehouseDestroyedController controller;

    @Test
    void pageAndResultsUseTheStoreFromTheSessionAndWarsawToday() {
        // given
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("period", "month");

        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn("store-1");

            // when
            String page = controller.page(params, Locale.ENGLISH, new ExtendedModelMap());
            String results = controller.results(params, Locale.ENGLISH, new ExtendedModelMap());

            // then
            assertThat(page).isEqualTo("warehouse-destroyed");
            assertThat(results).isEqualTo("warehouse-destroyed :: results");
            verify(service, times(2)).page(eq("store-1"), eq(false), argThat(q -> q.period() == DestroyedListQuery.Period.MONTH),
                    eq(LocalDate.now(WarehouseDestroyedController.ZONE)), eq(Locale.ENGLISH));
        }
    }
}
