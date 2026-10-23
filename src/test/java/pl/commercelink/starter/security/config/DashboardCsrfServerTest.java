package pl.commercelink.starter.security.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.thymeleaf.ThymeleafAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.MultipartAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import pl.commercelink.starter.secrets.SecretsManager;
import pl.commercelink.starter.security.filter.CustomTokenRefreshFilter;
import pl.commercelink.starter.security.handler.CustomAuthenticationSuccessHandler;
import pl.commercelink.starter.security.handler.CustomLogoutSuccessHandler;
import pl.commercelink.starter.security.service.CustomOAuth2UserService;
import pl.commercelink.web.ErrorController;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * The dashboard's CSRF protection through a real embedded Tomcat, which MockMvc does not stand in for: the container
 * parses a multipart upload before the CSRF check reads its token, Thymeleaf adds the token field to th:action forms,
 * and a refused request is forwarded to the access-denied page, which sends a browser without a session to the login
 * page. Signing in is replaced by a permitted test endpoint that puts a signed-in user into a new session.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = DashboardCsrfServerTest.ServerContext.class,
        properties = "application.env=localhost")
class DashboardCsrfServerTest {

    private static final Pattern FORM_TOKEN = Pattern.compile(
            "<form id=\"(\\w+)\"[^>]*>\\s*<input type=\"hidden\" name=\"_csrf\" value=\"([^\"]+)\"");
    private static final String BOUNDARY = "csrf-probe-boundary";

    @LocalServerPort
    private int port;

    @MockitoBean
    private CustomOAuth2UserService customOAuth2UserService;
    @MockitoBean
    private CustomAuthenticationSuccessHandler successHandler;
    @MockitoBean
    private CustomLogoutSuccessHandler logoutSuccessHandler;
    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    /** One browser: the cookies it keeps between requests. */
    private final class Browser {

        final Map<String, String> cookies = new LinkedHashMap<>();

        HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
            if (!cookies.isEmpty()) {
                request.header("Cookie", cookies.entrySet().stream()
                        .map(cookie -> cookie.getKey() + "=" + cookie.getValue()).collect(Collectors.joining("; ")));
            }
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            for (String header : response.headers().allValues("Set-Cookie")) {
                String pair = header.substring(0, header.indexOf(';') < 0 ? header.length() : header.indexOf(';'));
                String name = pair.substring(0, pair.indexOf('='));
                String value = pair.substring(pair.indexOf('=') + 1);
                if (value.isEmpty() || header.contains("Max-Age=0")) {
                    cookies.remove(name);
                } else {
                    cookies.put(name, value);
                }
            }
            return response;
        }

        HttpResponse<String> get(String path) throws Exception {
            return send(HttpRequest.newBuilder(uri(path)).GET());
        }

        HttpResponse<String> postForm(String path, String body) throws Exception {
            return send(HttpRequest.newBuilder(uri(path))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body)));
        }

        HttpResponse<String> postUpload(String token) throws Exception {
            StringBuilder body = new StringBuilder();
            body.append("--").append(BOUNDARY).append("\r\n")
                    .append("Content-Disposition: form-data; name=\"file\"; filename=\"products.csv\"\r\n")
                    .append("Content-Type: text/csv\r\n\r\n")
                    .append("ean;qty\r\n5901234123457;3\r\n");
            if (token != null) {
                body.append("--").append(BOUNDARY).append("\r\n")
                        .append("Content-Disposition: form-data; name=\"_csrf\"\r\n\r\n")
                        .append(token).append("\r\n");
            }
            body.append("--").append(BOUNDARY).append("--\r\n");
            return send(HttpRequest.newBuilder(uri("/dashboard/probe-upload"))
                    .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString())));
        }
    }

    private Browser signedIn() throws Exception {
        Browser browser = new Browser();
        browser.get("/register");
        return browser;
    }

    private static String formToken(HttpResponse<String> page, String formId) {
        Matcher matcher = FORM_TOKEN.matcher(page.body());
        while (matcher.find()) {
            if (matcher.group(1).equals(formId)) {
                return matcher.group(2);
            }
        }
        return null;
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private static String encoded(String value) {
        return value == null ? "" : URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @Test
    void postFormsCarryTheTokenAndGetFormsDoNot() throws Exception {
        // given
        Browser browser = signedIn();

        // when
        HttpResponse<String> page = browser.get("/dashboard/page");

        // then
        assertThat(formToken(page, "plain")).isNotBlank();
        assertThat(formToken(page, "upload")).isNotBlank();
        assertThat(formToken(page, "search")).isNull();
    }

    @Test
    void signedInFormWithItsTokenIsSaved() throws Exception {
        // given
        Browser browser = signedIn();
        HttpResponse<String> page = browser.get("/dashboard/page");

        // when
        HttpResponse<String> response = browser.postForm("/dashboard/probe",
                "note=hello&_csrf=" + encoded(formToken(page, "plain")));

        // then
        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    void signedInFormWithoutTheTokenIsRefused() throws Exception {
        // given
        Browser browser = signedIn();
        browser.get("/dashboard/page");

        // when
        HttpResponse<String> response = browser.postForm("/dashboard/probe", "note=hello");

        // then
        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    void tokenOfAnotherBrowserIsRefused() throws Exception {
        // given
        Browser victim = signedIn();
        victim.get("/dashboard/page");
        Browser attacker = signedIn();
        HttpResponse<String> attackerPage = attacker.get("/dashboard/page");

        // when
        HttpResponse<String> response = victim.postForm("/dashboard/probe",
                "_csrf=" + encoded(formToken(attackerPage, "plain")));

        // then
        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    void uploadWithItsTokenIsSaved() throws Exception {
        // given
        Browser browser = signedIn();
        HttpResponse<String> page = browser.get("/dashboard/page");

        // when
        HttpResponse<String> response = browser.postUpload(formToken(page, "upload"));

        // then
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).startsWith("ok ");
    }

    @Test
    void uploadWithoutTheTokenIsRefused() throws Exception {
        // given
        Browser browser = signedIn();
        browser.get("/dashboard/page");

        // when
        HttpResponse<String> response = browser.postUpload(null);

        // then
        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    void scriptSendingTheTokenInTheHeaderIsAccepted() throws Exception {
        // given
        Browser browser = signedIn();
        HttpResponse<String> page = browser.get("/dashboard/page");

        // when
        HttpResponse<String> response = browser.send(HttpRequest.newBuilder(uri("/dashboard/probe"))
                .header("X-CSRF-TOKEN", formToken(page, "plain"))
                .header("X-Requested-With", "fetch")
                .POST(HttpRequest.BodyPublishers.noBody()));

        // then
        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    void tokenOfAnEarlierPageStaysValid() throws Exception {
        // given
        Browser browser = signedIn();
        HttpResponse<String> earlier = browser.get("/dashboard/page");
        browser.get("/dashboard/page");

        // when
        HttpResponse<String> response = browser.postForm("/dashboard/probe",
                "_csrf=" + encoded(formToken(earlier, "plain")));

        // then
        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    void formSentAfterTheSessionWasLostLeadsToTheLoginPage() throws Exception {
        // given a deploy replaced the instance that kept the session
        Browser browser = signedIn();
        HttpResponse<String> page = browser.get("/dashboard/page");
        browser.cookies.remove("JSESSIONID");

        // when
        HttpResponse<String> response = browser.postForm("/dashboard/probe",
                "_csrf=" + encoded(formToken(page, "plain")));

        // then
        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("Location")).hasValueSatisfying(location ->
                assertThat(location).endsWith("/login"));
    }

    @Test
    void tokenFirstNeededLateInALongPageIsAccepted() throws Exception {
        // given a page whose first form comes after the response buffer was already sent
        Browser browser = signedIn();
        HttpResponse<String> page = browser.get("/dashboard/late");

        // when
        HttpResponse<String> response = browser.postForm("/dashboard/probe",
                "_csrf=" + encoded(formToken(page, "plain")));

        // then
        assertThat(page.body().length()).isGreaterThan(16 * 1024);
        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    void shopApiTakesPostsWithoutAToken() throws Exception {
        // when
        HttpResponse<String> response = new Browser().postForm("/Store/probe", "basket=1");

        // then
        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    void logoutNeedsNoTokenAndEndsTheSession() throws Exception {
        // given
        Browser browser = signedIn();
        browser.get("/dashboard/page");

        // when
        HttpResponse<String> logout = browser.postForm("/logout", "");

        // then
        assertThat(logout.statusCode()).isNotEqualTo(403);
        assertThat(browser.get("/dashboard/page").statusCode()).isEqualTo(302);
    }

    @SpringBootConfiguration
    @ImportAutoConfiguration({ServletWebServerFactoryAutoConfiguration.class, DispatcherServletAutoConfiguration.class,
            WebMvcAutoConfiguration.class, HttpMessageConvertersAutoConfiguration.class,
            MultipartAutoConfiguration.class, ThymeleafAutoConfiguration.class, SecurityAutoConfiguration.class,
            SecurityFilterAutoConfiguration.class})
    @Import({WebSecurityConfiguration.class, ErrorController.class, ProbeController.class})
    static class ServerContext {

        @Bean
        CustomTokenRefreshFilter tokenRefreshFilter() {
            return new CustomTokenRefreshFilter(mock(OAuth2AuthorizedClientService.class),
                    mock(SecretsManager.class), mock(Environment.class)) {
                @Override
                public void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                             FilterChain filterChain) throws ServletException, IOException {
                    filterChain.doFilter(request, response);
                }
            };
        }
    }

    @Controller
    static class ProbeController {

        // A permitted path stands in for the Cognito sign-in: it puts a signed-in user into a new session.
        @GetMapping("/register")
        @ResponseBody
        String signIn(HttpServletRequest request, HttpServletResponse response) {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new TestingAuthenticationToken("admin", null, "ROLE_ADMIN"));
            new HttpSessionSecurityContextRepository().saveContext(context, request, response);
            return "signed in";
        }

        @GetMapping("/dashboard/page")
        String page() {
            return "csrf-probe";
        }

        @GetMapping("/dashboard/late")
        String latePage() {
            return "csrf-probe-late";
        }

        @PostMapping("/dashboard/probe")
        @ResponseBody
        String post() {
            return "ok";
        }

        @PostMapping("/dashboard/probe-upload")
        @ResponseBody
        String upload(@RequestParam("file") MultipartFile file) {
            return "ok " + file.getSize();
        }

        @PostMapping("/Store/probe")
        @ResponseBody
        String storeApi() {
            return "ok";
        }
    }
}
