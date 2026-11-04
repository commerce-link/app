package pl.commercelink.carriers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Translates through our carrier: a text of system A comes in as a carrier ({@link #carrierFor}), a carrier goes out as
 * a text of system B ({@link #textFor}). Every lookup is one GetItem of the dictionary the caller names.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CarrierTranslator {

    private final CarrierDictionaryRepository repository;

    /** Our carrier for a text of the dictionary; a text it does not know is kept as it came, with a WARN. */
    public String carrierFor(String dictionary, String text) {
        Optional<String> carrier = recognized(dictionary, text);
        if (carrier.isEmpty() && StringUtils.isNotBlank(text)) {
            log.warn("Carrier text '{}' is not in dictionary {}, kept as it came", text, dictionary);
        }
        return carrier.orElse(text);
    }

    /** The text of the first pair with this carrier. */
    public Optional<String> textFor(String dictionary, String carrier) {
        if (StringUtils.isBlank(carrier)) {
            return Optional.empty();
        }
        return repository.get(dictionary).textFor(carrier);
    }

    /**
     * The text of the target dictionary for what a shipment holds as its carrier. Newer shipments hold our carrier name;
     * older ones a label code ("dhl") or the marketplace's own text, which the given dictionaries turn into a carrier first.
     */
    public Optional<String> textFor(String dictionary, String shipmentCarrier, List<String> sourceDictionaries) {
        Optional<String> direct = textFor(dictionary, shipmentCarrier);
        if (direct.isPresent() || StringUtils.isBlank(shipmentCarrier)) {
            return direct;
        }
        for (String source : sourceDictionaries) {
            Optional<String> text = recognized(source, shipmentCarrier).flatMap(carrier -> textFor(dictionary, carrier));
            if (text.isPresent()) {
                return text;
            }
        }
        return Optional.empty();
    }

    /** The carrier of the first pair with this text; in marketplace and shipping dictionaries, else by fragment. */
    private Optional<String> recognized(String dictionary, String text) {
        if (StringUtils.isBlank(text)) {
            return Optional.empty();
        }
        CarrierDictionary entries = repository.get(dictionary);
        Optional<String> exact = entries.carrierFor(text);
        if (exact.isPresent() || !CarrierDictionary.matchesByFragment(dictionary)) {
            return exact;
        }
        Optional<String> fragment = entries.carrierByFragmentFor(text);
        // fragments are a rule, not data: the log is how a wrong hit (a seller's own Ceneo form name) gets noticed
        fragment.ifPresent(carrier -> log.info("Carrier text '{}' recognized by fragment in {} as {}", text, dictionary, carrier));
        return fragment;
    }
}
