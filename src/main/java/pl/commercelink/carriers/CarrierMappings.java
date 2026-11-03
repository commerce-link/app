package pl.commercelink.carriers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Translates through our carrier: a text of system A comes in as a carrier, a carrier goes out as a text of system B.
 * Every lookup is one GetItem of the dictionary the caller names.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CarrierMappings {

    private final CarrierDictionaryRepository repository;

    /** The carrier of the first pair with this text; in marketplace and shipping dictionaries, else by fragment. */
    public Optional<String> inboundMappingFor(String dictionary, String text) {
        if (StringUtils.isBlank(text)) {
            return Optional.empty();
        }
        CarrierDictionary entries = repository.get(dictionary);
        Optional<String> exact = entries.inboundMappingFor(text);
        if (exact.isPresent() || !CarrierDictionary.matchesByFragment(dictionary)) {
            return exact;
        }
        Optional<String> fragment = entries.fragmentMappingFor(text);
        // fragments are a rule, not data: the log is how a wrong hit (a seller's own Ceneo form name) gets noticed
        fragment.ifPresent(carrier -> log.info("Carrier text '{}' recognized by fragment in {} as {}", text, dictionary, carrier));
        return fragment;
    }

    /** The carrier name for a recognized text, otherwise the text itself. */
    public String carrierNameFor(String dictionary, String text) {
        return recognize(dictionary, text).orElse(text);
    }

    /** Like inboundMappingFor, with a WARN naming the dictionary when a text is not recognized. */
    public Optional<String> recognize(String dictionary, String text) {
        Optional<String> carrier = inboundMappingFor(dictionary, text);
        if (carrier.isEmpty() && StringUtils.isNotBlank(text)) {
            log.warn("Carrier text '{}' is not in dictionary {}, kept as it came", text, dictionary);
        }
        return carrier;
    }

    public Optional<String> outboundMappingFor(String dictionary, String carrier) {
        if (StringUtils.isBlank(carrier)) {
            return Optional.empty();
        }
        return repository.get(dictionary).outboundMappingFor(carrier);
    }

    /**
     * The text of the target dictionary for what a shipment holds as its carrier. Newer shipments hold our carrier name;
     * older ones a label code ("dhl") or the marketplace's own text, which the given dictionaries turn into a carrier first.
     */
    public Optional<String> outboundMappingFor(String dictionary, String shipmentCarrier, List<String> sourceDictionaries) {
        Optional<String> direct = outboundMappingFor(dictionary, shipmentCarrier);
        if (direct.isPresent() || StringUtils.isBlank(shipmentCarrier)) {
            return direct;
        }
        for (String source : sourceDictionaries) {
            Optional<String> code = inboundMappingFor(source, shipmentCarrier)
                    .flatMap(carrier -> outboundMappingFor(dictionary, carrier));
            if (code.isPresent()) {
                return code;
            }
        }
        return Optional.empty();
    }
}
