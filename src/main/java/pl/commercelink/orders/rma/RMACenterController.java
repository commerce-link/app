package pl.commercelink.orders.rma;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import pl.commercelink.inventory.supplier.SupplierIdentity;
import pl.commercelink.inventory.supplier.SupplierLabelMap;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.starter.security.CustomSecurityContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
@Controller
@RequestMapping("/dashboard/store/rma-centers")
public class RMACenterController {

    @Autowired
    private StoresRepository storesRepository;

    @Autowired
    private RMACentersRepository rmaCentersRepository;

    @Autowired
    private SupplierRegistry supplierRegistry;

    @Autowired
    private SupplierLabels supplierLabels;

    @GetMapping
    public String list(Model model) {
        List<RMACenter> centers = rmaCentersRepository.findByStoreId(getRmaCenterStoreId());

        if (CustomSecurityContext.hasRole("ADMIN")) {
            Store store = storesRepository.findById(CustomSecurityContext.getStoreId());
            // Platform-wide centres are keyed by supplier type while store connections carry
            // tokened identities, so a type match on any connection also reveals the centre.
            centers = centers.stream()
                    .filter(c -> !supplierRegistry.exists(c.getProvider())
                            || SupplierRegistry.OTHER.equalsIgnoreCase(c.getProvider())
                            || store.getEnabledProviders().contains(c.getProvider())
                            || store.getEnabledProviders().stream()
                                    .anyMatch(name -> SupplierIdentity.typeOf(name).equals(c.getProvider())))
                    .toList();
        }

        model.addAttribute("scope", getRmaCenterStoreId());
        model.addAttribute("rmaCenters", centers.stream().sorted(Comparator.comparing(RMACenter::getProvider)));
        model.addAttribute("supplierLabels", CustomSecurityContext.hasRole("ADMIN")
                ? supplierLabels.forStoreId(CustomSecurityContext.getStoreId()) : supplierLabels.forStore(null));
        return "rma-centers";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        RMACenter rmaCenter = new RMACenter();
        rmaCenter.setStoreId(getRmaCenterStoreId());
        rmaCenter.setShippingDetails(new ShippingDetails());
        model.addAttribute("rmaCenter", rmaCenter);
        model.addAttribute("providerOptions", providerOptions());
        return "rma-center-form";
    }

    @GetMapping("/{rmaCenterId}/edit")
    public String editForm(@PathVariable String rmaCenterId, Model model) {
        RMACenter existingCenter = rmaCentersRepository.findById(getRmaCenterStoreId(), rmaCenterId);
        if (existingCenter == null) {
            model.addAttribute("error", "RMA Center not found");
            return "error";
        }

        model.addAttribute("rmaCenter", existingCenter);
        model.addAttribute("providerOptions", providerOptions());
        return "rma-center-form";
    }

    // Store admins pick one of their connections; the platform-wide (super admin) centres are keyed by supplier type.
    private List<SupplierLabelMap.Option> providerOptions() {
        List<SupplierLabelMap.Option> options = new ArrayList<>();
        if (CustomSecurityContext.hasRole("ADMIN")) {
            options.addAll(supplierLabels.forStoreId(CustomSecurityContext.getStoreId()).options());
        } else {
            for (String name : supplierRegistry.getExternalSupplierNames()) {
                options.add(new SupplierLabelMap.Option(name, name));
            }
        }
        options.add(new SupplierLabelMap.Option(SupplierRegistry.OTHER, SupplierRegistry.OTHER));
        return options;
    }

    @PostMapping("/save")
    public String save(@ModelAttribute RMACenter rmaCenter) {
        if (rmaCenter.getRmaCenterId() == null || rmaCenter.getRmaCenterId().isEmpty()) {
            rmaCenter.setRmaCenterId(UUID.randomUUID().toString());
        }
        rmaCentersRepository.save(rmaCenter);
        return "redirect:/dashboard/store/rma-centers";
    }

    @PostMapping("/{rmaCenterId}/delete")
    public String delete(@PathVariable String rmaCenterId) {
        RMACenter existing = rmaCentersRepository.findById(getRmaCenterStoreId(), rmaCenterId);
        if (existing != null) {
            rmaCentersRepository.delete(existing);
        }
        return "redirect:/dashboard/store/rma-centers";
    }

    private String getRmaCenterStoreId() {
        if (CustomSecurityContext.hasRole("SUPER_ADMIN")) {
            return RMACenter.MANAGED_RMA_CENTER_STORE_ID;
        }
        return CustomSecurityContext.getStoreId();
    }
}
