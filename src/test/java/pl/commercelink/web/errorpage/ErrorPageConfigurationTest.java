package pl.commercelink.web.errorpage;

import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.ModelAndView;
import pl.commercelink.stores.StoresRepository;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ErrorPageConfigurationTest {

    // Spring Boot drops its own error view resolver once the context has another one: a missing dashboard page must
    // still find error/404 instead of the Whitelabel page
    @Test
    void aMissingDashboardPageStillGetsTheError404Template() {
        // given
        GenericApplicationContext context = new GenericApplicationContext();
        context.refresh();
        PublicErrorViewResolver resolver = new ErrorPageConfiguration()
                .publicErrorViewResolver(mock(StoresRepository.class), context);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/dashboard/nie-ma");

        // when
        ModelAndView page = resolver.resolveErrorView(request, HttpStatus.NOT_FOUND, Map.of());

        // then
        assertThat(page).isNotNull();
        assertThat(page.getViewName()).isEqualTo("error/404");
    }
}
