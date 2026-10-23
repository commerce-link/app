package pl.commercelink.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import pl.commercelink.orders.history.ItemHistory;
import pl.commercelink.orders.history.ItemHistoryService;
import pl.commercelink.starter.security.model.CustomUser;
import pl.commercelink.web.itemhistory.ItemHistoryPage;
import pl.commercelink.web.itemhistory.ItemHistoryPageFactory;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ItemHistoryControllerTest {

    static final Locale PL = Locale.forLanguageTag("pl");

    @Mock private ItemHistoryService historyService;
    @Mock private ItemHistoryPageFactory pageFactory;
    @InjectMocks private ItemHistoryController controller;

    @BeforeEach
    void loggedInAsStoreAdmin() {
        CustomUser user = new CustomUser(null, null, Map.of("storeId", "store-1", "role", "ADMIN"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void searchesTheTrimmedNumberInTheSessionStore() {
        // given
        ItemHistory history = mock(ItemHistory.class);
        ItemHistoryPage page = mock(ItemHistoryPage.class);
        when(historyService.history("store-1", "SN-1")).thenReturn(history);
        when(pageFactory.of(history, PL)).thenReturn(page);
        Model model = new ExtendedModelMap();

        // when
        String view = controller.viewHistory("  SN-1 ", model, PL);

        // then
        assertThat(view).isEqualTo("item-history");
        assertThat(model.getAttribute("page")).isSameAs(page);
    }

    @Test
    void aBlankNumberShowsTheSearchPageWithoutReading() {
        // given
        ItemHistoryPage empty = mock(ItemHistoryPage.class);
        when(pageFactory.empty()).thenReturn(empty);
        Model model = new ExtendedModelMap();

        // when
        controller.viewHistory("   ", model, PL);

        // then
        assertThat(model.getAttribute("page")).isSameAs(empty);
        verify(historyService, never()).history(anyString(), anyString());
    }

    @Test
    void staysClosedToTheSuperAdmin() {
        // then
        assertThat(ItemHistoryController.class.getAnnotation(PreAuthorize.class).value()).isEqualTo("!hasRole('SUPER_ADMIN')");
    }
}
