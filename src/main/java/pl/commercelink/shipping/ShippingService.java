package pl.commercelink.shipping;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.orders.ShippingForm;
import pl.commercelink.orders.rma.InvalidReturnConfigurationException;
import pl.commercelink.shipping.api.*;
import pl.commercelink.starter.util.OperationResult;
import pl.commercelink.stores.AuthorizedCarrier;
import pl.commercelink.stores.BankAccount;
import pl.commercelink.stores.PackageTemplate;
import pl.commercelink.stores.Store;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ShippingService {

    @Autowired
    private ShippingProviderFactory shippingProviderFactory;

    public List<ShippingEstimate> estimateServicePrices(ShippingForm form, Store store) {
        ShippingDetails pickupAddress = store.getPickUpAddress(form.getPickUpAddressId());
        ShippingDetails senderAddress = store.getDefaultSenderAddress().orElse(pickupAddress);

        ShipmentRequest request = ShipmentRequest.builder()
                .pickup(toShipmentAddress(pickupAddress))
                .sender(toShipmentAddress(senderAddress))
                .receiver(toShipmentAddress(form.getShippingDetails()))
                .parcels(toParcels(form.getCompleteParcels()))
                .options(new ShipmentOptions(
                        form.isSaturdayDelivery(),
                        false,
                        form.isCashOnDelivery()
                                ? new ShipmentOptions.CashOnDelivery(form.getCashOnDeliveryAmount(), null, null, null)
                                : null))
                .build();


        Set<String> carrierIds = store.getShippingConfiguration().getAuthorizedCarriers().stream()
                .map(AuthorizedCarrier::getId)
                .collect(Collectors.toSet());

        ShippingProvider shippingProvider = shippingProviderFactory.get(store);
        return shippingProvider.estimateShipment(request, carrierIds);
    }

    public OperationResult<List<Shipment>> createShipping(ShippingForm form, Store store) {
        ShippingDetails pickupAddress = store.getPickUpAddress(form.getPickUpAddressId());
        ShippingDetails senderAddress = store.getDefaultSenderAddress().orElse(pickupAddress);

        ShipmentOptions.CashOnDelivery cod = null;
        if (form.isCashOnDelivery()) {
            BankAccount bankAccount = store.getDefaultBankAccount();
            cod = new ShipmentOptions.CashOnDelivery(
                    form.getCashOnDeliveryAmount(), bankAccount.getIban(), bankAccount.getAccountHolder(), bankAccount.getSwiftCode());
        }

        ShipmentRequest request = ShipmentRequest.builder()
                .pickup(toShipmentAddress(pickupAddress))
                .sender(toShipmentAddress(senderAddress))
                .receiver(toShipmentAddress(form.getShippingDetails()))
                .parcels(toParcels(form.getCompleteParcels()))
                .carrierId(form.getServiceId())
                .options(new ShipmentOptions(form.isSaturdayDelivery(), false, cod))
                .build();

        return executeCreateShipment(request, store);
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

    public OperationResult<List<Shipment>> createShipping(ShippingDetails pickupAddress, List<ParcelForm> parcels, Carrier carrier, Store store) {
        ShippingDetails receiverAddress = store.getDefaultPickupAddress()
                .orElseThrow(() -> new InvalidReturnConfigurationException(
                        "Default receiver address not configured. Contact store administrator."
                ));

        ShipmentRequest request = ShipmentRequest.builder()
                .pickup(toShipmentAddress(pickupAddress))
                .receiver(toShipmentAddress(receiverAddress))
                .parcels(toParcels(parcels))
                .carrierId(carrier.id())
                .options(new ShipmentOptions(false, true, null))
                .build();

        return executeCreateShipment(request, store);
    }

    private OperationResult<List<Shipment>> executeCreateShipment(ShipmentRequest request, Store store) {
        ShippingProvider shippingProvider = shippingProviderFactory.get(store);
        try {
            ShipmentResult result = shippingProvider.createShipment(request);
            return OperationResult.success(toShipments(result));
        } catch (ShippingException e) {
            return OperationResult.failure(e.getMessage());
        }
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

    private static List<Parcel> toParcels(List<ParcelForm> parcels) {
        return parcels.stream()
                .map(p -> new Parcel(p.getWidth(), p.getDepth(), p.getHeight(), p.getWeight(), p.getValue(), p.getDescription(), p.getType()))
                .collect(Collectors.toList());
    }

    private static List<Shipment> toShipments(ShipmentResult result) {
        return result.parcels().stream()
                .map(parcel -> {
                    Shipment shipment = new Shipment();
                    shipment.setExternalId(result.externalId());
                    shipment.setTrackingNo(parcel.trackingNo());
                    shipment.setCarrier(parcel.carrier());
                    shipment.setTrackingUrl(parcel.trackingUrl());
                    shipment.setShippedAt(LocalDateTime.now());
                    return shipment;
                })
                .collect(Collectors.toList());
    }
}