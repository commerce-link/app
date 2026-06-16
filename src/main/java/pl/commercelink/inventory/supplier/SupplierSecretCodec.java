package pl.commercelink.inventory.supplier;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.TreeMap;

@Component
public class SupplierSecretCodec {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String toSecretString(Map<String, String> config) {
        if (config == null || config.isEmpty()) {
            return "";
        }
        if (config.size() == 1) {
            return config.values().iterator().next();
        }
        try {
            return objectMapper.writeValueAsString(new TreeMap<>(config));
        } catch (Exception e) {
            throw new RuntimeException("Failed to encode supplier secret", e);
        }
    }
}
