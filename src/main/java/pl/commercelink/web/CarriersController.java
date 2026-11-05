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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/** Our carriers and the shared dictionaries that translate them, for the super admin. */
@Controller
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class CarriersController {

    static final String PATH = "/dashboard/carriers";
    static final String DICTIONARY_PATH = PATH + "/dictionary";
    private static final String SAVED_DICTIONARY = "savedDictionary";

    private final CarriersRepository carriersRepository;
    private final CarrierDictionaryRepository dictionaryRepository;
    private final CarrierDictionaryWriter dictionaryWriter;
    private final MessageSource messageSource;
    private final MarketplaceProviderFactory marketplaces;
    private final ShippingProviderFactory shipping;

    public record DictionaryRow(String key, String title, String description, String whenEmpty, int pairs, String href) {
    }

    /** The dictionaries of one integration, so the page reads per marketplace or shipping integration. */
    public record IntegrationDictionaries(String heading, List<DictionaryRow> dictionaries) {
    }

    private enum Kind {
        IMPORT("import"), STATUS("status"), SHIPPING("shipping");

        private final String messageKey;

        Kind(String messageKey) {
            this.messageKey = "carriers.dictionary.kind." + messageKey;
        }
    }

    private record SharedDictionary(String key, Kind kind, String integration, String heading) {
    }

    @GetMapping(PATH)
    public String carriers(Model model, Locale locale) {
        Map<String, List<DictionaryRow>> byIntegration = new LinkedHashMap<>();
        for (SharedDictionary d : sharedDictionaries(locale)) {
            byIntegration.computeIfAbsent(d.heading(), heading -> new ArrayList<>())
                    .add(new DictionaryRow(d.key(), message(d.kind().messageKey + ".title", locale),
                            description(d, locale), message(d.kind().messageKey + ".empty", locale),
                            dictionaryRepository.get(d.key()).getMappings().size(),
                            dictionaryHref(d.key())));
        }
        model.addAttribute("carriers", carriersRepository.findAll());
        model.addAttribute("integrations", byIntegration.entrySet().stream()
                .map(e -> new IntegrationDictionaries(e.getKey(), e.getValue())).toList());
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
        SharedDictionary shared = requireShared(dictionary, locale);
        CarrierDictionary entry = model.getAttribute(SAVED_DICTIONARY) instanceof CarrierDictionary saved
                && saved.getDictionary().equals(dictionary) ? saved : dictionaryRepository.get(dictionary);
        return dictionaryPage(model, shared, entry.getMappings(), entry.getVersion(), null, locale);
    }

    @PostMapping(DICTIONARY_PATH)
    public String saveDictionary(@RequestParam String dictionary,
                                 @RequestParam(required = false) Long version,
                                 @RequestParam(name = "externalText", required = false) List<String> externalTexts,
                                 @RequestParam(name = "carrier", required = false) List<String> carriers,
                                 Locale locale, Model model, RedirectAttributes redirectAttributes) {
        SharedDictionary shared = requireShared(dictionary, locale);
        List<CarrierMapping> pairs = CarrierDictionaryForm.pairs(externalTexts, carriers);
        try {
            CarrierDictionaryWriter.Result result = dictionaryWriter.save(dictionary, pairs, version);
            if (!result.isSaved()) {
                String error = message("carriers.dictionary.refused." + result.refusal().name(), locale,
                        result.externalText());
                return dictionaryPage(model, shared, pairs, version, error, locale);
            }
            // the page after the redirect shows what was saved: an eventually consistent read could still return the
            // previous version, and the next save from that page would be refused as a concurrent edit
            redirectAttributes.addFlashAttribute(SAVED_DICTIONARY, result.saved());
        } catch (ConditionalCheckFailedException e) {
            return dictionaryPage(model, shared, pairs, version, message("carriers.dictionary.conflict", locale), locale);
        }
        SettingsFlash.onRedirect(redirectAttributes, message("carriers.dictionary.saved", locale, title(shared, locale)));
        return "redirect:" + dictionaryHref(dictionary);
    }

    private String dictionaryPage(Model model, SharedDictionary shared, List<CarrierMapping> pairs, Long version,
                                  String error, Locale locale) {
        String dictionary = shared.key();
        model.addAttribute("title", title(shared, locale));
        model.addAttribute("description", description(shared, locale));
        model.addAttribute("externalTextColumn", message(shared.kind().messageKey + ".column", locale));
        model.addAttribute("pairs", CarrierDictionaryForm.withEmptyRow(pairs));
        model.addAttribute("version", version);
        model.addAttribute("carrierNames", carriersRepository.findAll().stream().map(Carrier::getName).toList());
        model.addAttribute("hints", List.of(message("carriers.dictionary.order", locale),
                message(CarrierDictionary.matchesByFragment(dictionary)
                        ? "carriers.dictionary.fragments" : "carriers.dictionary.exact", locale)));
        model.addAttribute("dictionaryKey", dictionary);
        model.addAttribute("formAction", DICTIONARY_PATH);
        model.addAttribute("backHref", PATH);
        model.addAttribute("backLabel", message("carriers.title", locale));
        model.addAttribute(SettingsFlash.ERROR_MESSAGE, error);
        return CarrierDictionaryForm.VIEW;
    }

    /** Every installed marketplace and shipping adapter has its dictionaries; supplier ones belong to the stores. */
    private List<SharedDictionary> sharedDictionaries(Locale locale) {
        List<SharedDictionary> dictionaries = new ArrayList<>();
        for (ProviderDescriptor<?> marketplace : sorted(marketplaces.availableProviders())) {
            String name = marketplace.displayName();
            String heading = message("carriers.dictionaries.group.marketplace", locale, name);
            dictionaries.add(new SharedDictionary(CarrierDictionary.marketplaceOrderCarrierTexts(marketplace.name()),
                    Kind.IMPORT, name, heading));
            dictionaries.add(new SharedDictionary(CarrierDictionary.marketplaceStatusCarrierCodes(marketplace.name()),
                    Kind.STATUS, name, heading));
        }
        for (ProviderDescriptor<?> integration : sorted(shipping.availableProviders())) {
            String name = integration.displayName();
            dictionaries.add(new SharedDictionary(CarrierDictionary.shippingLabelCarrierCodes(integration.name()),
                    Kind.SHIPPING, name, message("carriers.dictionaries.group.shipping", locale, name)));
        }
        return dictionaries;
    }

    private static <D extends ProviderDescriptor<?>> List<D> sorted(Collection<D> descriptors) {
        return descriptors.stream().sorted(Comparator.comparing((Function<D, String>) ProviderDescriptor::name)).toList();
    }

    // store dictionaries (supplier connections) are edited by the store, not here
    private SharedDictionary requireShared(String dictionary, Locale locale) {
        return sharedDictionaries(locale).stream()
                .filter(shared -> shared.key().equals(dictionary))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private String title(SharedDictionary dictionary, Locale locale) {
        return message("carriers.dictionary.page.title", locale, dictionary.integration(),
                message(dictionary.kind().messageKey + ".title", locale));
    }

    private String description(SharedDictionary dictionary, Locale locale) {
        return message(dictionary.kind().messageKey + ".description", locale, dictionary.integration());
    }

    static String dictionaryHref(String dictionary) {
        return UriComponentsBuilder.fromPath(DICTIONARY_PATH).queryParam("dictionary", "{d}")
                .encode().buildAndExpand(dictionary).toUriString();
    }

    private String message(String key, Locale locale, Object... args) {
        return messageSource.getMessage(key, args, locale);
    }
}
