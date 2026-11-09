package pl.commercelink.carriers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Translates through our carrier: an external text of system A comes in as a carrier ({@link #carrierFor}), a carrier
 * goes out as an external text of system B ({@link #externalTextFor}). Every lookup is one GetItem of the dictionary
 * the caller names.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CarrierTranslator {

    private final CarrierDictionaryRepository repository;

    // an unknown text is kept rather than dropped, so the shipment still shows what the external system said
    public String carrierFor(String dictionary, String externalText) {
        Optional<String> carrier = recognizedCarrier(dictionary, externalText);
        if (carrier.isEmpty() && StringUtils.isNotBlank(externalText)) {
            log.warn("Carrier text '{}' is not in dictionary {}, kept as it came", externalText, dictionary);
        }
        return carrier.orElse(externalText);
    }

    public Optional<String> externalTextFor(String dictionary, String carrier) {
        if (StringUtils.isBlank(carrier)) {
            return Optional.empty();
        }
        return repository.get(dictionary).externalTextFor(carrier);
    }

    /**
     * Newer shipments hold our carrier name; older ones a label code ("dhl") or the marketplace's own text, which the
     * source dictionaries turn into a carrier first.
     */
    public Optional<String> externalTextForShipmentCarrier(String dictionary, String shipmentCarrier,
                                                           List<String> sourceDictionaries) {
        Optional<String> direct = externalTextFor(dictionary, shipmentCarrier);
        if (direct.isPresent() || StringUtils.isBlank(shipmentCarrier)) {
            return direct;
        }
        for (String source : sourceDictionaries) {
            Optional<String> externalText = recognizedCarrier(source, shipmentCarrier)
                    .flatMap(carrier -> externalTextFor(dictionary, carrier));
            if (externalText.isPresent()) {
                return externalText;
            }
        }
        return Optional.empty();
    }

    private Optional<String> recognizedCarrier(String dictionary, String externalText) {
        if (StringUtils.isBlank(externalText)) {
            return Optional.empty();
        }
        CarrierDictionary entries = repository.get(dictionary);
        Optional<String> exact = entries.carrierFor(externalText);
        if (exact.isPresent() || !CarrierDictionary.matchesByFragment(dictionary)) {
            return exact;
        }
        Optional<String> fragment = entries.carrierByFragmentFor(externalText);
        // fragments are a rule, not data: the log is how a wrong hit (a seller's own Ceneo form name) gets noticed
        fragment.ifPresent(carrier -> log.info("Carrier text '{}' recognized by fragment in {} as {}",
                externalText, dictionary, carrier));
        return fragment;
    }
}
