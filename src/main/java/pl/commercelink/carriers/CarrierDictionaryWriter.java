package pl.commercelink.carriers;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The only way a dictionary is written: the whole list at once, conditional on the version the editor started from.
 * Carriers are not checked against the Carriers table, since the forms offer only carriers from it.
 */
@Service
@RequiredArgsConstructor
public class CarrierDictionaryWriter {

    private final CarrierDictionaryRepository repository;

    public enum Refusal {
        EMPTY_PAIR, DUPLICATE_PAIR, AMBIGUOUS_SUPPLIER_TEXT
    }

    public record Result(Refusal refusal, String externalText, CarrierDictionary saved) {

        static Result refused(Refusal refusal, String externalText) {
            return new Result(refusal, externalText, null);
        }

        static Result saved(CarrierDictionary saved) {
            return new Result(null, null, saved);
        }

        public boolean isSaved() {
            return refusal == null;
        }
    }

    /** @throws com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException when someone saved it in between */
    public Result save(String dictionary, List<CarrierMapping> pairs, Long version) {
        List<CarrierMapping> normalized = new ArrayList<>();
        for (CarrierMapping pair : pairs) {
            String externalText = CarrierDictionary.normalize(pair.getExternalText());
            String carrier = StringUtils.trimToNull(pair.getCarrier());
            if (externalText == null && carrier == null) {
                continue;
            }
            if (externalText == null || carrier == null) {
                return Result.refused(Refusal.EMPTY_PAIR, StringUtils.defaultString(externalText));
            }
            normalized.add(new CarrierMapping(externalText, carrier));
        }
        Optional<Result> refusal = validate(dictionary, normalized);
        if (refusal.isPresent()) {
            return refusal.get();
        }
        CarrierDictionary entry = new CarrierDictionary(dictionary, normalized);
        entry.setVersion(version);
        repository.save(entry);
        return Result.saved(entry);
    }

    private static Optional<Result> validate(String dictionary, List<CarrierMapping> pairs) {
        boolean supplier = CarrierDictionary.isSupplier(dictionary);
        Set<String> seenPairs = new HashSet<>();
        Map<String, String> carrierByExternalText = new HashMap<>();
        for (CarrierMapping pair : pairs) {
            String externalText = pair.getExternalText().toUpperCase(Locale.ROOT);
            if (!seenPairs.add(externalText + "\n" + pair.getCarrier().toUpperCase(Locale.ROOT))) {
                return Optional.of(Result.refused(Refusal.DUPLICATE_PAIR, pair.getExternalText()));
            }
            // a supplier's method has to name one carrier, otherwise its parcels could not be told apart
            String other = carrierByExternalText.putIfAbsent(externalText, pair.getCarrier());
            if (supplier && other != null && !other.equalsIgnoreCase(pair.getCarrier())) {
                return Optional.of(Result.refused(Refusal.AMBIGUOUS_SUPPLIER_TEXT, pair.getExternalText()));
            }
        }
        return Optional.empty();
    }
}
