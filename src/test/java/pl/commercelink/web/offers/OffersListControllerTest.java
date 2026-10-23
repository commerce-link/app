package pl.commercelink.web.offers;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.util.LinkedMultiValueMap;
import pl.commercelink.starter.security.CustomSecurityContext;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OffersListControllerTest {

    @Mock
    private OfferListService service;
    @InjectMocks
    private OffersListController controller;

    private MockedStatic<CustomSecurityContext> security;

    @BeforeEach
    void setup() {
        security = mockStatic(CustomSecurityContext.class);
        security.when(CustomSecurityContext::getStoreId).thenReturn("store-1");
    }

    @AfterEach
    void tearDown() {
        security.close();
    }

    @Test
    void pageReadsTheStoreOfTheSession() {
        // given
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.offers(new LinkedMultiValueMap<>(), Locale.forLanguageTag("pl"), model);

        // then
        assertThat(view).isEqualTo("offers");
        verify(service).page(eq("store-1"), any(OfferListQuery.class), any(), any());
        assertThat(model).containsKey("page");
    }

    @Test
    void fragmentReturnsOnlyTheResultsBlock() {
        // when / then
        assertThat(controller.offersList(new LinkedMultiValueMap<>(), Locale.forLanguageTag("pl"), new ExtendedModelMap()))
                .isEqualTo("offers :: results");
    }

    @Test
    void oldSearchFormRedirects() {
        // given
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("name", "CAD");

        // when
        String view = controller.offers(params, Locale.forLanguageTag("pl"), new ExtendedModelMap());

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/offers?q=CAD");
        verifyNoInteractions(service);
    }
}
