package pl.commercelink.web;

import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriUtils;
import pl.commercelink.carriers.Carrier;
import pl.commercelink.carriers.CarrierDictionary;
import pl.commercelink.carriers.CarrierDictionaryRepository;
import pl.commercelink.carriers.CarrierDictionaryWriter;
import pl.commercelink.carriers.CarrierMapping;
import pl.commercelink.carriers.CarriersRepository;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreSupplierConnection;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.settings.SettingsFlash;
import pl.commercelink.web.settings.SettingsPaths;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Settings › Suppliers › a supplier › Carriers: how the delivery methods of one supplier connection (e.g. one Shoper
 * shop) translate to our carriers, so the parcels of its dropship deliveries get our carrier. Each connection has a
 * dictionary of its own, edited by the store; the shared dictionaries of marketplaces and shipping integrations stay
 * with the super admin. A price list uploaded by hand has no parcels, so it has no such page.
 */
@Controller
@RequiredArgsConstructor
public class StoreSupplierCarriersController {

    // the whole segment, as on the supplier pages: an identity may hold any character the operator typed
    private static final String IDENTITY = "/{identity:.+}";
    private static final String CARRIERS = "/carriers";
    private static final String SAVED_DICTIONARY = "savedDictionary";

    private final StoresRepository storesRepository;
    private final SupplierConnections suppliers;
    private final CarriersRepository carriersRepository;
    private final CarrierDictionaryRepository dictionaryRepository;
    private final CarrierDictionaryWriter dictionaryWriter;
    private final MessageSource messageSource;

    @GetMapping("/dashboard/store/suppliers" + IDENTITY + CARRIERS)
    @PreAuthorize("hasRole('ADMIN')")
    public String carriers(@PathVariable String identity, Model model, Locale locale) {
        return show(CustomSecurityContext.getStoreId(), identity, model, locale);
    }

    @GetMapping("/dashboard/store/{storeId}/suppliers" + IDENTITY + CARRIERS)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminCarriers(@PathVariable String storeId, @PathVariable String identity, Model model,
                                     Locale locale) {
        return show(storeId, identity, model, locale);
    }

    @PostMapping("/dashboard/store/suppliers" + IDENTITY + CARRIERS)
    @PreAuthorize("hasRole('ADMIN')")
    public String save(@PathVariable String identity,
                       @RequestParam(required = false) Long version,
                       @RequestParam(name = "externalText", required = false) List<String> externalTexts,
                       @RequestParam(name = "carrier", required = false) List<String> carriers,
                       Locale locale, Model model, RedirectAttributes redirectAttributes) {
        return save(CustomSecurityContext.getStoreId(), identity, version, externalTexts, carriers, locale, model,
                redirectAttributes);
    }

    @PostMapping("/dashboard/store/{storeId}/suppliers" + IDENTITY + CARRIERS)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminSave(@PathVariable String storeId, @PathVariable String identity,
                                 @RequestParam(required = false) Long version,
                                 @RequestParam(name = "externalText", required = false) List<String> externalTexts,
                                 @RequestParam(name = "carrier", required = false) List<String> carriers,
                                 Locale locale, Model model, RedirectAttributes redirectAttributes) {
        return save(storeId, identity, version, externalTexts, carriers, locale, model, redirectAttributes);
    }

    private String show(String storeId, String identity, Model model, Locale locale) {
        StoreSupplierConnection connection = requireConnection(storeId, identity);
        String dictionary = CarrierDictionary.supplierDeliveryMethods(storeId, connection.getSupplierName());
        CarrierDictionary entry = model.getAttribute(SAVED_DICTIONARY) instanceof CarrierDictionary saved
                && saved.getDictionary().equals(dictionary) ? saved : dictionaryRepository.get(dictionary);
        return page(model, storeId, connection, entry.getMappings(), entry.getVersion(), null, locale);
    }

    private String save(String storeId, String identity, Long version, List<String> externalTexts,
                        List<String> carriers, Locale locale, Model model, RedirectAttributes redirectAttributes) {
        StoreSupplierConnection connection = requireConnection(storeId, identity);
        String dictionary = CarrierDictionary.supplierDeliveryMethods(storeId, connection.getSupplierName());
        List<CarrierMapping> pairs = CarrierDictionaryForm.pairs(externalTexts, carriers);
        Optional<String> unknown = unknownCarrier(dictionary, pairs);
        if (unknown.isPresent()) {
            return page(model, storeId, connection, pairs, version,
                    message("store.supplier.carriers.unknown", locale, unknown.get()), locale);
        }
        try {
            CarrierDictionaryWriter.Result result = dictionaryWriter.save(dictionary, pairs, version);
            if (!result.isSaved()) {
                String error = message("carriers.dictionary.refused." + result.refusal().name(), locale,
                        result.externalText());
                return page(model, storeId, connection, pairs, version, error, locale);
            }
            // the page after the redirect shows what was saved: an eventually consistent read could still return the
            // previous version, and the next save from that page would be refused as a concurrent edit
            redirectAttributes.addFlashAttribute(SAVED_DICTIONARY, result.saved());
        } catch (ConditionalCheckFailedException e) {
            return page(model, storeId, connection, pairs, version, message("carriers.dictionary.conflict", locale),
                    locale);
        }
        SettingsFlash.onRedirect(redirectAttributes,
                message("store.supplier.carriers.saved", locale, SupplierLabels.labelOf(connection)));
        return "redirect:" + carriersPath(storeId, connection.getSupplierName());
    }

    // the carriers are the platform's: a store picks from them, and keeps one its dictionary already holds
    private Optional<String> unknownCarrier(String dictionary, List<CarrierMapping> pairs) {
        Set<String> allowed = new HashSet<>(carriersRepository.findAll().stream().map(Carrier::getName).toList());
        dictionaryRepository.get(dictionary).getMappings().forEach(pair -> allowed.add(pair.getCarrier()));
        return pairs.stream()
                .map(CarrierMapping::getCarrier)
                .filter(carrier -> carrier != null && !carrier.isBlank() && !allowed.contains(carrier.trim()))
                .findFirst();
    }

    private String page(Model model, String storeId, StoreSupplierConnection connection, List<CarrierMapping> pairs,
                        Long version, String error, Locale locale) {
        String identity = connection.getSupplierName();
        String label = SupplierLabels.labelOf(connection);
        model.addAttribute("title", message("store.supplier.carriers.title", locale, label));
        model.addAttribute("description", message("store.supplier.carriers.description", locale, label));
        model.addAttribute("externalTextColumn", message("store.supplier.carriers.column", locale));
        model.addAttribute("pairs", CarrierDictionaryForm.withEmptyRow(pairs));
        model.addAttribute("version", version);
        model.addAttribute("carrierNames", carriersRepository.findAll().stream().map(Carrier::getName).toList());
        model.addAttribute("hints", List.of(message("store.supplier.carriers.hint", locale),
                message("carriers.dictionary.exact", locale)));
        model.addAttribute("formAction", carriersPath(storeId, identity));
        model.addAttribute("backHref", SettingsPaths.store(storeId, "/suppliers"));
        model.addAttribute("backLabel", message("store.suppliers", locale));
        model.addAttribute(SettingsFlash.ERROR_MESSAGE, error);
        return CarrierDictionaryForm.VIEW;
    }

    static String carriersPath(String storeId, String identity) {
        return SettingsPaths.store(storeId, "/suppliers/"
                + UriUtils.encodePathSegment(identity, StandardCharsets.UTF_8) + CARRIERS);
    }

    private StoreSupplierConnection requireConnection(String storeId, String identity) {
        Store store = storesRepository.findById(storeId);
        StoreSupplierConnection connection = store == null ? null : suppliers.connection(store, identity);
        if (connection == null || connection.getMode() == ConnectionMode.MANUAL) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return connection;
    }

    private String message(String key, Locale locale, Object... args) {
        return messageSource.getMessage(key, args, locale);
    }
}
