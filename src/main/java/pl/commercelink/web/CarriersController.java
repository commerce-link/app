package pl.commercelink.web;

import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;
import pl.commercelink.carriers.Carrier;
import pl.commercelink.carriers.CarrierDictionaries;
import pl.commercelink.carriers.CarrierDictionary;
import pl.commercelink.carriers.CarrierDictionaryRepository;
import pl.commercelink.carriers.CarrierDictionaryWriter;
import pl.commercelink.carriers.CarrierMapping;
import pl.commercelink.carriers.CarriersRepository;
import pl.commercelink.web.settings.SettingsFlash;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Our carriers and the shared dictionaries that translate them, for the super admin. */
@Controller
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class CarriersController {

    static final String PATH = "/dashboard/carriers";
    static final String DICTIONARY_PATH = PATH + "/dictionary";

    private final CarriersRepository carriersRepository;
    private final CarrierDictionaryRepository dictionaryRepository;
    private final CarrierDictionaryWriter dictionaryWriter;
    private final MessageSource messageSource;

    public record DictionaryRow(String key, int pairs, String href) {
    }

    public record MissingStatusCode(String carrier, String importDictionary, String statusDictionary) {
    }

    @GetMapping(PATH)
    public String carriers(Model model) {
        List<DictionaryRow> dictionaries = CarrierDictionaries.shared().stream()
                .map(key -> new DictionaryRow(key, dictionaryRepository.get(key).getMappings().size(), dictionaryHref(key)))
                .toList();
        model.addAttribute("carriers", carriersRepository.findAll());
        model.addAttribute("dictionaries", dictionaries);
        model.addAttribute("missingStatusCodes", missingStatusCodes());
        return "carriers";
    }

    @PostMapping(PATH)
    public String createCarrier(@RequestParam String name, @RequestParam(required = false) String description,
                                Locale locale, RedirectAttributes redirectAttributes) {
        String trimmed = CarrierDictionary.normalize(name);
        if (trimmed == null) {
            SettingsFlash.errorOnRedirect(redirectAttributes, message("carriers.create.empty", locale));
        } else if (carriersRepository.create(new Carrier(trimmed, StringUtils.trimToNull(description)))) {
            SettingsFlash.onRedirect(redirectAttributes, message("carriers.create.saved", locale, trimmed));
        } else {
            SettingsFlash.errorOnRedirect(redirectAttributes, message("carriers.create.exists", locale, trimmed));
        }
        return "redirect:" + PATH;
    }

    @GetMapping(DICTIONARY_PATH)
    public String dictionary(@RequestParam String dictionary, Model model) {
        CarrierDictionary entry = dictionaryRepository.get(requireShared(dictionary));
        return dictionaryPage(model, dictionary, entry.getMappings(), entry.getVersion(), null);
    }

    @PostMapping(DICTIONARY_PATH)
    public String saveDictionary(@RequestParam String dictionary,
                                 @RequestParam(required = false) Long version,
                                 @RequestParam(name = "text", required = false) List<String> texts,
                                 @RequestParam(name = "carrier", required = false) List<String> carriers,
                                 Locale locale, Model model, RedirectAttributes redirectAttributes) {
        requireShared(dictionary);
        List<CarrierMapping> pairs = pairs(texts, carriers);
        try {
            CarrierDictionaryWriter.Result result = dictionaryWriter.save(dictionary, pairs, version);
            if (!result.isSaved()) {
                String error = message("carriers.dictionary.refused." + result.refusal().name(), locale, result.text());
                return dictionaryPage(model, dictionary, pairs, version, error);
            }
        } catch (ConditionalCheckFailedException e) {
            return dictionaryPage(model, dictionary, pairs, version, message("carriers.dictionary.conflict", locale));
        }
        SettingsFlash.onRedirect(redirectAttributes, message("carriers.dictionary.saved", locale, dictionary));
        return "redirect:" + dictionaryHref(dictionary);
    }

    private String dictionaryPage(Model model, String dictionary, List<CarrierMapping> pairs, Long version, String error) {
        model.addAttribute("dictionary", dictionary);
        model.addAttribute("pairs", withEmptyRow(pairs));
        model.addAttribute("version", version);
        model.addAttribute("carrierNames", carriersRepository.names());
        model.addAttribute("fragments", CarrierDictionaries.matchesByFragment(dictionary));
        model.addAttribute(SettingsFlash.ERROR_MESSAGE, error);
        return "carrier-dictionary";
    }

    // the last empty row is how a pair is added without JavaScript; an empty row is not saved
    private static List<CarrierMapping> withEmptyRow(List<CarrierMapping> pairs) {
        List<CarrierMapping> rows = new ArrayList<>(pairs);
        CarrierMapping last = rows.isEmpty() ? null : rows.get(rows.size() - 1);
        if (last == null || StringUtils.isNotBlank(last.getText()) || StringUtils.isNotBlank(last.getCarrier())) {
            rows.add(new CarrierMapping());
        }
        return rows;
    }

    private List<MissingStatusCode> missingStatusCodes() {
        List<MissingStatusCode> missing = new ArrayList<>();
        for (Map.Entry<String, String> pair : CarrierDictionaries.requiredStatusCodes()) {
            CarrierDictionary status = dictionaryRepository.get(pair.getValue());
            dictionaryRepository.get(pair.getKey()).getMappings().stream()
                    .map(CarrierMapping::getCarrier)
                    .distinct()
                    .filter(carrier -> status.outboundMappingFor(carrier).isEmpty())
                    .forEach(carrier -> missing.add(new MissingStatusCode(carrier, pair.getKey(), pair.getValue())));
        }
        return missing;
    }

    private static List<CarrierMapping> pairs(List<String> texts, List<String> carriers) {
        List<String> t = texts == null ? List.of() : texts;
        List<String> c = carriers == null ? List.of() : carriers;
        List<CarrierMapping> pairs = new ArrayList<>();
        for (int i = 0; i < Math.max(t.size(), c.size()); i++) {
            pairs.add(new CarrierMapping(i < t.size() ? t.get(i) : null, i < c.size() ? c.get(i) : null));
        }
        return pairs;
    }

    // store dictionaries (supplier connections) are edited by the store, not here
    private static String requireShared(String dictionary) {
        if (!CarrierDictionaries.shared().contains(dictionary)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return dictionary;
    }

    static String dictionaryHref(String dictionary) {
        return UriComponentsBuilder.fromPath(DICTIONARY_PATH).queryParam("dictionary", "{d}")
                .encode().buildAndExpand(dictionary).toUriString();
    }

    private String message(String key, Locale locale, Object... args) {
        return messageSource.getMessage(key, args, locale);
    }
}
