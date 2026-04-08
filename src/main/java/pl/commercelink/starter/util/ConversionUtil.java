package pl.commercelink.starter.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.commons.lang3.StringUtils;

import java.io.Reader;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class ConversionUtil {

    public static final String DATE_FORMAT = "yyyy-MM-dd";
    public static final String DATE_TIME_FORMAT = "dd MMM yyyy HH:mm:ss";

    // Create a shared ObjectMapper instance with consistent configuration
    private static final ObjectMapper OBJECT_MAPPER = createObjectMapper();

    private static ObjectMapper createObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.setSerializationInclusion(JsonInclude.Include.ALWAYS);
        return objectMapper;
    }

    public static double asDouble(Object obj) {
        String value = asString(obj);
        return Double.parseDouble(isNotEmpty(value) ? value : "0");
    }

    public static int asInt(Object obj) {
        String value = asString(obj);
        if (value.endsWith(".0")) {
            value = value.split("\\.")[0];
        }
        return Integer.parseInt(isNotEmpty(value) ? value : "0");
    }

    public static int asIntOrDefault(Object[] obj, int index, int defaultValue) {
        return obj.length > index ? asInt(obj[index]) : defaultValue;
    }

    public static long asLong(Object obj) {
        String value = asString(obj);
        return Long.parseLong(isNotEmpty(value) ? value : "0");
    }

    public static String asString(Object obj) {
        return String.valueOf(obj);
    }

    public static boolean asBoolean(Object obj) {
        return Boolean.parseBoolean(asString(obj));
    }

    public static boolean asBooleanOrDefault(Collection<Object> obj, int index, boolean defaultValue) {
        return obj.size() > index ? asBoolean(obj.toArray()[index]) : defaultValue;
    }

    public static String asStringOrDefault(Object[] obj, int index, String defaultValue) {
        return obj.length > index ? asString(obj[index]) : defaultValue;
    }

    public static String asStringOrDefault(Collection<Object> obj, int index, String defaultValue) {
        return obj.size() > index ? asString(obj.toArray()[index]) : defaultValue;
    }

    public static LocalDate asLocalDateOrDefault(Collection<Object> obj, int index, LocalDate defaultValue) {
        return obj.size() > index ? asLocalDate(obj.toArray()[index]) : defaultValue;
    }

    public static LocalDate asLocalDate(Object obj) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_FORMAT);
        return LocalDate.parse(asString(obj), formatter);
    }

    public static String asLocallyFormattedDate(LocalDateTime date) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_TIME_FORMAT);
        return date.format(formatter);
    }

    public static String asLocallyFormattedDate(Instant instant) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_TIME_FORMAT);
        LocalDateTime dateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        return dateTime.format(formatter);
    }

    public static String asLocallyFormattedDate(long timestamp) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_TIME_FORMAT);
        LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
        return dateTime.format(formatter);
    }

    public static Map<String, String> asMap(String value) {
        if (value == null || value.isEmpty()) {
            return new HashMap<>();
        }

        Map<String, String> map = new HashMap<>();
        for (String entry : value.split(",")) {
            String[] kv = entry.split("=");

            map.put(kv[0], kv[1]);
        }
        return map;
    }

    public static Collection<String> asDistinctCollectionFromStream(Stream<String> col) {
        return col
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    public static Collection<String> asDistinctCollectionFromCommaSeparateText(String txt) {
        if (txt == null || txt.isEmpty()) {
            return new HashSet<>();
        }

        return Arrays.stream(txt.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toSet());
    }

    public static boolean isNotEmpty(String val) {
        return val != null && !val.isEmpty();
    }

    public static String toJson(Object object) {
        try {
            return OBJECT_MAPPER.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize object to JSON", e);
        }
    }

    public static <T> T fromJson(String jsonString, Class<T> clazz) {
        try {
            return OBJECT_MAPPER.readValue(jsonString, clazz);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize JSON to " + clazz.getSimpleName(), e);
        }
    }

    public static <T> T fromJson(Reader reader, Class<T> clazz) {
        try {
            return OBJECT_MAPPER.readValue(reader, clazz);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize JSON to " + clazz.getSimpleName(), e);
        }
    }

    public static <T> byte[] fromJsonToBytes(T object) {
        try {
            return OBJECT_MAPPER.writeValueAsBytes(object);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize JSON to byte[]", e);
        }
    }

    public static <T> List<T> join(List<? extends T> list1, List<? extends T> list2) {
        List<T> result = new LinkedList<>(list1);
        result.addAll(list2);
        return result;
    }

    public static String guessValueType(String value) {
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern(DATE_FORMAT);
        try {
            LocalDate.parse(value, dateFormatter);
            return "date";
        } catch (DateTimeParseException e) {
            // Not a date
        }

        try {
            LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            return "date-time";
        } catch (DateTimeParseException e) {
            // Not a date-time
        }

        if (value.matches("\\d+")) {
            return "integer";
        } else if (value.matches("\\d+\\.\\d+")) {
            return "double";
        } else {
            return "text";
        }
    }

    public static <T> T getMostFrequentValue(Stream<T> values) {
        return values
                .collect(Collectors.groupingBy(s -> s, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    public static String getShortenedId(String id) {
        if (isNotBlank(id) && id.indexOf("-") == 8) {
            return id.substring(0, id.indexOf("-"));
        }
        return id;
    }
}
