package pl.commercelink.web.deliveries.details;

import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.orders.Order;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * What the controller resolved for one delivery details request. preselected: indexes of the allocations checked in a
 * form posted back without JavaScript; openDialog/openMfn: the dialog the page renders open (?open=, ?mfn=).
 */
public record DeliveryPageData(Delivery delivery, String supplierName, String partnerSiteUrl, List<Delivery> mergeTargets,
                               Order dropshipOrder, List<String> carrierOptions, LocalDate suggestedEstimatedDeliveryAt,
                               Set<Integer> preselected, String openDialog, String openMfn, LocalDateTime now) {

    public DeliveryPageData {
        mergeTargets = mergeTargets == null ? List.of() : mergeTargets;
        carrierOptions = carrierOptions == null ? List.of() : carrierOptions;
        preselected = preselected == null ? Set.of() : preselected;
    }
}
