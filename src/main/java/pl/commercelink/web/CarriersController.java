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
import pl.commercelink.carriers.CarrierDictionary;
import pl.commercelink.carriers.CarrierDictionaryRepository;
import pl.commercelink.carriers.CarrierDictionaryWriter;
import pl.commercelink.carriers.CarrierMapping;
import pl.commercelink.carriers.CarriersRepository;
import pl.commercelink.marketplace.MarketplaceProviderFactory;
import pl.commercelink.provider.api.ProviderDescriptor;
import pl.commercelink.shipping.ShippingProviderFactory;
import pl.commercelink.web.settings.SettingsFlash;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

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
    private final MarketplaceProviderFactory marketplaces;
    private final ShippingProviderFactory shipping;

    public record DictionaryRow(String key, String purpose, int pairs, String href) {
    }

    private record SharedDictionary(String key, String purpose, String integration) {
    }

    @GetMapping(PATH)
    public String carriers(Model model, Locale locale) {
        List<DictionaryRow> dictionaries = sharedDictionaries().stream()
                .map(d -> new DictionaryRow(d.key(), purpose(d, locale),
                        dictionaryRepository.get(d.key()).getMappings().size(), dictionaryHref(d.key())))
                .toList();
        model.addAttribute("carriers", carriersRepository.findAllForEditing());
        model.addAttribute("dictionaries", dictionaries);
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
    public String dictionary(@RequestParam String dictionary, Model model, Locale locale) {
        SharedDictionary shared = requireShared(dictionary);
        CarrierDictionary entry = dictionaryRepository.getForEditing(dictionary);
        return dictionaryPage(model, shared, entry.getMappings(), entry.getVersion(), null, locale);
    }

    @PostMapping(DICTIONARY_PATH)
    public String saveDictionary(@RequestParam String dictionary,
                                 @RequestParam(required = false) Long version,
                                 @RequestParam(name = "text", required = false) List<String> texts,
                                 @RequestParam(name = "carrier", required = false) List<String> carriers,
                                 Locale locale, Model model, RedirectAttributes redirectAttributes) {
        SharedDictionary shared = requireShared(dictionary);
        List<CarrierMapping> pairs = pairs(texts, carriers);
        try {
            CarrierDictionaryWriter.Result result = dictionaryWriter.save(dictionary, pairs, version);
            if (!result.isSaved()) {
                String error = message("carriers.dictionary.refused." + result.refusal().name(), locale, result.text());
                return dictionaryPage(model, shared, pairs, version, error, locale);
            }
        } catch (ConditionalCheckFailedException e) {
            return dictionaryPage(model, shared, pairs, version, message("carriers.dictionary.conflict", locale), locale);
        }
        SettingsFlash.onRedirect(redirectAttributes, message("carriers.dictionary.saved", locale, dictionary));
        return "redirect:" + dictionaryHref(dictionary);
    }

    private String dictionaryPage(Model model, SharedDictionary shared, List<CarrierMapping> pairs, Long version,
                                  String error, Locale locale) {
        String dictionary = shared.key();
        model.addAttribute("dictionary", dictionary);
        model.addAttribute("purpose", purpose(shared, locale));
        model.addAttribute("pairs", withEmptyRow(pairs));
        model.addAttribute("version", version);
        model.addAttribute("carrierNames", carriersRepository.findAllForEditing().stream().map(Carrier::getName).toList());
        model.addAttribute("fragments", CarrierDictionary.matchesByFragment(dictionary));
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

    /** Every installed marketplace and shipping adapter has its dictionaries; supplier ones belong to the stores. */
    private List<SharedDictionary> sharedDictionaries() {
        List<SharedDictionary> dictionaries = new ArrayList<>();
        for (ProviderDescriptor<?> marketplace : sorted(marketplaces.availableProviders())) {
            dictionaries.add(new SharedDictionary(CarrierDictionary.marketplaceImport(marketplace.name()),
                    "carriers.dictionary.purpose.import", marketplace.displayName()));
            dictionaries.add(new SharedDictionary(CarrierDictionary.marketplaceStatus(marketplace.name()),
                    "carriers.dictionary.purpose.status", marketplace.displayName()));
        }
        for (ProviderDescriptor<?> integration : sorted(shipping.availableProviders())) {
            dictionaries.add(new SharedDictionary(CarrierDictionary.shipping(integration.name()),
                    "carriers.dictionary.purpose.shipping", integration.displayName()));
        }
        return dictionaries;
    }

    private static <D extends ProviderDescriptor<?>> List<D> sorted(Collection<D> descriptors) {
        return descriptors.stream().sorted(Comparator.comparing((Function<D, String>) ProviderDescriptor::name)).toList();
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
    private SharedDictionary requireShared(String dictionary) {
        return sharedDictionaries().stream()
                .filter(shared -> shared.key().equals(dictionary))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private String purpose(SharedDictionary dictionary, Locale locale) {
        return message(dictionary.purpose(), locale, dictionary.integration());
    }

    static String dictionaryHref(String dictionary) {
        return UriComponentsBuilder.fromPath(DICTIONARY_PATH).queryParam("dictionary", "{d}")
                .encode().buildAndExpand(dictionary).toUriString();
    }

    private String message(String key, Locale locale, Object... args) {
        return messageSource.getMessage(key, args, locale);
    }
}
