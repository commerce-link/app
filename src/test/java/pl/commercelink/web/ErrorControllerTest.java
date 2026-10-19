package pl.commercelink.web;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class ErrorControllerTest {

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ErrorController()).build();

    // Spring Security's accessDeniedPage forwards the denied request with its own method: a POST denied to a USER
    // (e.g. "Pobierz wpłaty z systemu fakturowego") must render the 403 page, not answer 405
    @ParameterizedTest
    @ValueSource(strings = {"GET", "POST", "PUT", "DELETE"})
    void theAccessDeniedPageAnswersEveryMethod(String method) throws Exception {
        // when / then
        mvc.perform(request(HttpMethod.valueOf(method), "/access-denied"))
                .andExpect(status().isOk())
                .andExpect(view().name("error/403"));
    }
}
