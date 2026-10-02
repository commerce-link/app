package pl.commercelink.web.payments;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.util.LinkedMultiValueMap;
import pl.commercelink.starter.security.CustomSecurityContext;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentsControllerTest {

    @Mock PaymentsModelFactory factory;
    @InjectMocks PaymentsController controller;

    @Test
    void pageUsesTheSessionStore() {
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            // given
            security.when(CustomSecurityContext::getStoreId).thenReturn("store-1");
            ExtendedModelMap model = new ExtendedModelMap();

            // when
            String view = controller.payments(new LinkedMultiValueMap<>(), new Locale("pl"), model);

            // then
            assertThat(view).isEqualTo("payments");
            assertThat(model).containsKeys("page", "paymentSources");
            verify(factory).page(eq("store-1"), any(), any(), any());
        }
    }

    @Test
    void fragmentRendersOnlyTheResults() {
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn("store-1");
            assertThat(controller.fragment(new LinkedMultiValueMap<>(), new Locale("pl"), new ExtendedModelMap()))
                    .isEqualTo("payments :: results");
        }
    }
}
