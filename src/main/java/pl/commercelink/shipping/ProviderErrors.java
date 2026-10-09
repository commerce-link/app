package pl.commercelink.shipping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import pl.commercelink.rest.client.HttpClientException;
import pl.commercelink.shipping.api.ShippingException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/** How a failed provider call is read: whether the command surely did not run, and what to show the operator. */
public final class ProviderErrors {

    private static final ObjectMapper JSON = new ObjectMapper();

    private ProviderErrors() {
    }

    /**
     * Only a clear refusal means the command was not run: a check before sending (no HTTP answer behind it) or a 4xx
     * answer. A 5xx, a timeout or any other error may come after the provider already accepted the command.
     */
    public static boolean isRefusal(RuntimeException e) {
        HttpClientException http = httpCause(e);
        if (http != null) {
            return http.getStatusCode() >= 400 && http.getStatusCode() < 500;
        }
        return e instanceof ShippingException;
    }

    /** The provider answered the command (an HTTP answer stands behind it), as opposed to the adapter's own words. */
    public static boolean isProviderAnswer(RuntimeException e) {
        return httpCause(e) != null;
    }

    /** The provider's own messages (errors[].message of a JSON body), else the exception's message. */
    public static String describe(RuntimeException e) {
        HttpClientException http = httpCause(e);
        if (http != null && http.getResponseBody() != null) {
            try {
                JsonNode errors = JSON.readTree(http.getResponseBody()).path("errors");
                List<String> messages = new ArrayList<>();
                errors.forEach(error -> {
                    if (error.hasNonNull("message")) {
                        messages.add(error.get("message").asText());
                    }
                });
                if (!messages.isEmpty()) {
                    return String.join("; ", messages);
                }
            } catch (Exception ignored) {
                // not JSON: the message below says what happened
            }
        }
        return e.getMessage();
    }

    private static HttpClientException httpCause(Throwable e) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable current = e; current != null && seen.add(current); current = current.getCause()) {
            if (current instanceof HttpClientException http) {
                return http;
            }
        }
        return null;
    }
}
