package pl.commercelink.shipping;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.orders.ShippingForm;
import pl.commercelink.orders.rma.InvalidReturnConfigurationException;
import pl.commercelink.shipping.api.*;
import pl.commercelink.stores.AuthorizedCarrier;
import pl.commercelink.stores.BankAccount;
import pl.commercelink.stores.PackageTemplate;
import pl.commercelink.stores.Store;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import pl.commercelink.stores.IntegrationType;

@Slf4j
@Service
public class ShippingService {

    @Autowired
    private ShippingProviderFactory shippingProviderFactory;

    @Autowired
    private CarrierDictionary carrierDictionary;

    @Autowired
    private ShippingProviders shippingProviders;

    /**
     * Whether the store can price and book a courier: a shipping provider is connected and its adapter is installed.
     * The one rule behind the order's "Zamów kuriera" action and every step of the courier page. The adapter is looked
     * up by its descriptor, which is what makes ShippingProviderFactory#get return null, without loading the account's
     * settings on every order page.
     */
    public boolean isAvailable(Store store) {
        return store != null
                && shippingProviderFactory.getDescriptor(store.getConfigurationValue(IntegrationType.SHIPPING_PROVIDER)) != null;
    }

    /**
     * Whether "Nadaj przesyłkę" can work for this order: the store's default integration is there, or the store ships
     * orders placed on Allegro through Wysyłam z Allegro and this is one. Asks nobody: whether Allegro accepts the
     * order's delivery method is said by the page itself (ShippingIntegrationChoice).
     */
    public boolean isAvailableFor(Store store, Order order) {
        return isAvailable(store) || (store != null
                && store.hasShippingIntegration(ShippingIntegrationChoice.ALLEGRO)
                && ShippingIntegrationChoice.isAllegroOrder(order));
    }

    public List<ShippingEstimate> estimateServicePrices(ShippingForm form, Store store, DeliveryTarget deliveryTarget) {
        ShippingProvider shippingProvider = providerFor(store);
        ShippingDetails pickupAddress = store.getPickUpAddress(form.getPickUpAddressId());
        ShippingDetails senderAddress = store.getDefaultSenderAddress().orElse(pickupAddress);

        ShipmentRequest request = ShipmentRequest.builder()
                .pickup(toShipmentAddress(pickupAddress))
                .sender(toShipmentAddress(senderAddress))
                .receiver(toReceiverAddress(form.getShippingDetails(), deliveryTarget.pointCode()))
                .parcels(toParcels(form.getCompleteParcels()))
                .deliveryPoint(toDeliveryPoint(deliveryTarget.pointCode()))
                .options(new ShipmentOptions(
                        form.isSaturdayDelivery(),
                        false,
                        form.isCashOnDelivery()
                                ? new ShipmentOptions.CashOnDelivery(form.getCashOnDeliveryAmount(), null, null, null)
                                : null))
                .build();


        Set<String> carrierIds = carriersMatching(carrierDictionary, store.getConfigurationValue(IntegrationType.SHIPPING_PROVIDER),
                deliveryTarget.source(), store.getShippingConfiguration().getAuthorizedCarriers(), deliveryTarget.carrier()).stream()
                .map(AuthorizedCarrier::getId)
                .collect(Collectors.toSet());

        return shippingProvider.estimateShipment(request, carrierIds);
    }

    /** The store's provider, or ShippingUnavailableException when none is connected or its adapter is missing. */
    public ShippingProvider providerFor(Store store) {
        ShippingProvider shippingProvider = isAvailable(store) ? shippingProviderFactory.get(store) : null;
        if (shippingProvider == null) {
            throw new ShippingUnavailableException(store == null ? null : store.getStoreId());
        }
        return shippingProvider;
    }

    /** The integration of a shipment or a pickup group, if the store still has it and its adapter is installed. */
    public Optional<ShippingProvider> providerNamed(Store store, String provider) {
        return shippingProviders.forName(store, provider);
    }

    /** The store's address a shipment leaves from, for the pickup calls; null when it leaves from the customer. */
    public ShipmentAddress pickupAddress(Store store, String pickUpAddressId) {
        if (store == null || pickUpAddressId == null) {
            return null;
        }
        ShippingDetails details = store.getPickUpAddress(pickUpAddressId);
        return details == null ? null : toShipmentAddress(details);
    }

    /**
     * "Pobierz etykietę" can work for a package of this integration: the store still has it (a label lives on the
     * account that created it) and its adapter hands out labels. Loads the account, so pages ask once per integration.
     */
    public boolean supportsLabels(Store store, String provider) {
        if (store == null || provider == null) {
            return false;
        }
        try {
            return providerNamed(store, provider).map(ShippingProvider::supportsLabels).orElse(false);
        } catch (RuntimeException e) {
            // the link is left out, the page itself still shows; the label endpoint says why when asked directly
            log.warn("Shipping provider {} of store {} could not be loaded to offer labels", provider, store.getStoreId(), e);
            return false;
        }
    }

    public String providerName(Store store) {
        return store.getConfigurationValue(IntegrationType.SHIPPING_PROVIDER);
    }

    public ShipmentRequest buildRequest(ShippingForm form, Store store, DeliveryTarget deliveryTarget) {
        // a store without a provider fails with the domain reason here, not on a missing address below
        providerFor(store);
        ShippingDetails pickupAddress = store.getPickUpAddress(form.getPickUpAddressId());
        ShippingDetails senderAddress = store.getDefaultSenderAddress().orElse(pickupAddress);

        ShipmentOptions.CashOnDelivery cod = null;
        if (form.isCashOnDelivery()) {
            BankAccount bankAccount = store.getDefaultBankAccount();
            cod = new ShipmentOptions.CashOnDelivery(
                    form.getCashOnDeliveryAmount(), bankAccount.getIban(), bankAccount.getAccountHolder(), bankAccount.getSwiftCode());
        }

        return ShipmentRequest.builder()
                .pickup(toShipmentAddress(pickupAddress))
                .sender(toShipmentAddress(senderAddress))
                .receiver(toReceiverAddress(form.getShippingDetails(), deliveryTarget.pointCode()))
                .parcels(toParcels(form.getCompleteParcels()))
                .carrierId(form.getServiceId())
                .deliveryPoint(toDeliveryPoint(deliveryTarget.pointCode()))
                .options(new ShipmentOptions(form.isSaturdayDelivery(), false, cod))
                .build();
    }

    /** A customer return: picked up at the customer's address, delivered to the store's default pickup address. */
    public ShipmentRequest buildReturnRequest(ShippingDetails customerAddress, List<ParcelForm> parcels, Carrier carrier, Store store) {
        providerFor(store);
        ShippingDetails receiverAddress = store.getDefaultPickupAddress()
                .orElseThrow(() -> new InvalidReturnConfigurationException(
                        "Default receiver address not configured. Contact store administrator."));
        return ShipmentRequest.builder()
                .pickup(toShipmentAddress(customerAddress))
                .receiver(toShipmentAddress(receiverAddress))
                .parcels(toParcels(parcels))
                .carrierId(carrier.id())
                .options(new ShipmentOptions(false, true, null))
                .build();
    }

    public List<ParcelForm> retrieveParcelsListBasedOnPackageTemplate(double totalPrice, String packageTemplateId, Store store) {
        PackageTemplate packageTemplate = store.getPackageTemplate(packageTemplateId);

        List<ParcelForm> parcels = packageTemplate.getParcels().stream()
                .map(p -> new ParcelForm(p.getDepth(), p.getWidth(), p.getHeight(), p.getWeight(), p.getValue(), p.getDescription(), "package"))
                .collect(Collectors.toList());

        if (!parcels.isEmpty()) {
            distributeInsuranceAcrossParcels(totalPrice, parcels);
        }
        return parcels;
    }
    // max insurance is 20k, we need to split it between parcels

    private void distributeInsuranceAcrossParcels(double totalPrice, List<ParcelForm> parcels) {
        final int MAX_INSURANCE = 50000;
        int perParcel = (int) Math.round(totalPrice / parcels.size());
        perParcel = Math.max(1, Math.min(perParcel, MAX_INSURANCE));

        for (ParcelForm parcel : parcels) {
            parcel.setValue(perParcel);
        }
    }

    static ShipmentAddress toReceiverAddress(ShippingDetails details, String pointCode) {
        if (pointCode == null) {
            return toShipmentAddress(details);
        }
        return new ShipmentAddress(
                details.getFullName(),
                details.isCompanyAddress() ? details.getCompanyName() : null,
                null,
                null,
                null,
                details.getCountry(),
                details.getEmail(),
                details.getPhone()
        );
    }

    private static ShipmentAddress toShipmentAddress(ShippingDetails details) {
        return new ShipmentAddress(
                details.getFullName(),
                details.isCompanyAddress() ? details.getCompanyName() : null,
                details.getStreetAndNumber(),
                details.getPostalCode(),
                details.getCity(),
                details.getCountry(),
                details.getEmail(),
                details.getPhone()
        );
    }

    static List<AuthorizedCarrier> carriersMatching(CarrierDictionary dictionary, String shippingProvider,
            String source, List<AuthorizedCarrier> authorizedCarriers, String shippingCarrier) {
        Optional<String> chosen = StringUtils.equalsIgnoreCase(shippingProvider, source)
                ? Optional.ofNullable(StringUtils.trimToNull(shippingCarrier))
                : dictionary.translate(source, shippingProvider, shippingCarrier);
        if (chosen.isEmpty()) {
            return authorizedCarriers;
        }
        List<AuthorizedCarrier> matching = authorizedCarriers.stream()
                .filter(carrier -> describes(chosen.get(), carrier))
                .collect(Collectors.toList());

        return matching.isEmpty() ? authorizedCarriers : matching;
    }

    private static boolean describes(String chosen, AuthorizedCarrier carrier) {
        return StringUtils.containsIgnoreCase(carrier.getName(), chosen)
                || StringUtils.containsIgnoreCase(carrier.getDisplayName(), chosen);
    }

    private static DeliveryPoint toDeliveryPoint(String pointCode) {
        return pointCode != null ? new DeliveryPoint(pointCode) : null;
    }

    private static List<Parcel> toParcels(List<ParcelForm> parcels) {
        return parcels.stream()
                .map(p -> new Parcel(p.getWidth(), p.getDepth(), p.getHeight(), p.getWeight(), p.getValue(), p.getDescription(), p.getType()))
                .collect(Collectors.toList());
    }
}
