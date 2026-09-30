package pl.commercelink.web.deliveries.pending;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import pl.commercelink.starter.security.CustomSecurityContext;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PendingDeliveriesControllerTest {

    @Mock
    private PendingDeliveriesService service;
    @InjectMocks
    private PendingDeliveriesController controller;

    @Test
    void storeAdminGetsHisStoresPageAndFragment() {
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            // given
            security.when(CustomSecurityContext::getStoreId).thenReturn("store-1");
            LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("kind", "dropship");

            // when
            String page = controller.pending(params, new Locale("pl"), new ExtendedModelMap());
            String fragment = controller.pendingFragment(params, new Locale("pl"), new ExtendedModelMap());

            // then
            assertThat(page).isEqualTo("deliveries/pending");
            assertThat(fragment).isEqualTo("deliveries/pending :: results");
            verify(service, times(2)).page(eq("store-1"), eq(false),
                    eq(new PendingDeliveriesQuery(PendingDeliveriesQuery.Kind.DROPSHIP, null, List.of(), null)), any(), any());
        }
    }

    @Test
    void superAdminGetsTheStoreFromThePath() {
        // when
        String page = controller.pendingForSuperAdmin("store-7", new LinkedMultiValueMap<>(), new Locale("pl"), new ExtendedModelMap());
        String fragment = controller.pendingFragmentForSuperAdmin("store-7", new LinkedMultiValueMap<>(), new Locale("pl"), new ExtendedModelMap());

        // then
        assertThat(page).isEqualTo("deliveries/pending");
        assertThat(fragment).isEqualTo("deliveries/pending :: results");
        verify(service, times(2)).page(eq("store-7"), eq(true), any(), any(), any());
    }

    @Test
    void everyEndpointIsRestrictedToItsRole() {
        // given
        Map<String, String> expected = Map.of(
                "pending", "hasRole('ADMIN')",
                "pendingFragment", "hasRole('ADMIN')",
                "pendingForSuperAdmin", "hasRole('SUPER_ADMIN')",
                "pendingFragmentForSuperAdmin", "hasRole('SUPER_ADMIN')");

        // when
        Map<String, String> actual = Arrays.stream(PendingDeliveriesController.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(GetMapping.class))
                .collect(Collectors.toMap(Method::getName,
                        m -> m.isAnnotationPresent(PreAuthorize.class) ? m.getAnnotation(PreAuthorize.class).value() : "none"));

        // then
        assertThat(actual).isEqualTo(expected);
    }
}
