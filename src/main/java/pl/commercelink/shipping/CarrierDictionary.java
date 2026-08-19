package pl.commercelink.shipping;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@ConfigurationProperties(prefix = "shipping")
public class CarrierDictionary {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, String>> CODES = new TypeReference<>() {
    };

    private Map<String, Map<String, String>> carriers = new LinkedHashMap<>();

    public Map<String, Map<String, String>> getCarriers() {
        return carriers;
    }

    public void setCarriers(Map<String, Map<String, String>> carriers) {
        this.carriers = carriers;
    }

    @PostConstruct
    void validate() {
        carriers.forEach((source, targets) -> targets.forEach((target, codes) -> parse(source, target, codes)));
    }

    public Optional<String> translate(String source, String target, String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        Map<String, String> codes = codesFor(source, target);
        String normalized = value.trim();
        return matchExactly(codes, normalized).or(() -> matchByContent(codes, normalized.toUpperCase()));
    }

    public boolean describes(String source, String target, String value, String expected) {
        return expected != null && translate(source, target, value)
                .filter(expected::equalsIgnoreCase)
                .isPresent();
    }

    private Optional<String> matchExactly(Map<String, String> codes, String normalized) {
        return codes.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(normalized))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    private Optional<String> matchByContent(Map<String, String> codes, String normalized) {
        return codes.entrySet().stream()
                .filter(entry -> normalized.contains(entry.getKey().toUpperCase()))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    public Set<String> namesUsedBy(String system) {
        return carriers.keySet().stream()
                .flatMap(source -> codesFor(source, system).values().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Map<String, String> codesFor(String source, String target) {
        return carriers.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(source))
                .flatMap(entry -> entry.getValue().entrySet().stream()
                        .filter(byTarget -> byTarget.getKey().equalsIgnoreCase(target))
                        .map(byTarget -> parse(source, target, byTarget.getValue())))
                .findFirst()
                .orElse(Map.of());
    }

    private Map<String, String> parse(String source, String target, String codes) {
        try {
            return MAPPER.readValue(codes, CODES);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Malformed carrier mapping shipping.carriers." + source + "." + target + ": " + codes, e);
        }
    }
}
