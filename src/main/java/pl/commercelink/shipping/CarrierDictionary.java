package pl.commercelink.shipping;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@ConfigurationProperties(prefix = "shipping")
public class CarrierDictionary {

    private Map<String, List<String>> carriers = new LinkedHashMap<>();

    public Map<String, List<String>> getCarriers() {
        return carriers;
    }

    public void setCarriers(Map<String, List<String>> carriers) {
        this.carriers = carriers;
    }

    public Optional<String> resolve(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String normalized = name.trim();
        return matchExactly(normalized).or(() -> matchByContent(normalized.toUpperCase()));
    }

    public boolean describes(String carrier, String name) {
        return carrier != null && resolve(name).filter(carrier::equalsIgnoreCase).isPresent();
    }

    private Optional<String> matchExactly(String normalized) {
        return carriers.keySet().stream()
                .filter(carrier -> namesOf(carrier).stream().anyMatch(alias -> alias.equalsIgnoreCase(normalized)))
                .findFirst();
    }

    private Optional<String> matchByContent(String normalized) {
        return carriers.keySet().stream()
                .filter(carrier -> namesOf(carrier).stream().anyMatch(alias -> normalized.contains(alias.toUpperCase())))
                .findFirst();
    }

    private List<String> namesOf(String carrier) {
        List<String> names = new ArrayList<>();
        names.add(carrier);
        names.add(carrier.replace('_', ' '));
        carriers.getOrDefault(carrier, List.of()).forEach(alias -> names.add(alias.trim()));
        return names;
    }
}
