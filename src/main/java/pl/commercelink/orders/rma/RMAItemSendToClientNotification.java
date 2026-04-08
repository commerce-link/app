package pl.commercelink.orders.rma;

import com.fasterxml.jackson.annotation.JsonProperty;
import pl.commercelink.starter.email.EmailNotification;
import pl.commercelink.orders.Shipment;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

class RMAItemSendToClientNotification extends EmailNotification {
    @JsonProperty("rmaId")
    private String rmaId;
    @JsonProperty("orderId")
    private String orderId;
    @JsonProperty("rmaItems")
    private List<ItemInfo> rmaItems;
    @JsonProperty("shipments")
    private List<ShipmentInfo> shipments;

    RMAItemSendToClientNotification(String recipientEmail, String recipientName, String rmaId, String orderId, List<RMAItem> rmaItems, List<Shipment> shipments) {
        super(recipientEmail, recipientName);
        this.rmaId = rmaId;
        this.orderId = orderId;
        this.rmaItems = rmaItems.stream()
                .map(i -> new ItemInfo(
                        i.getName(),
                        i.getStatus().getLocalizedName(new Locale("pl")),
                        i.getActualResolution() != null ? i.getActualResolution().name() : "N/A",
                        i.getReason()))
                .collect(Collectors.toList());
        this.shipments = shipments.stream().map(ShipmentInfo::new).collect(Collectors.toList());
    }

    public String getRmaId() {
        return rmaId;
    }

    public String getOrderId() {
        return orderId;
    }

    public List<ItemInfo> getRmaItems() {
        return rmaItems;
    }

    public List<ShipmentInfo> getShipments() {
        return shipments;
    }

    static class ItemInfo {
        @JsonProperty("status")
        private final String name;
        @JsonProperty("status")
        private final String status;
        @JsonProperty("resolution")
        private final String resolution;
        @JsonProperty("reason")
        private final String reason;

        public ItemInfo(String name, String status, String resolution, String reason) {
            this.name = name;
            this.status = status;
            this.resolution = resolution;
            this.reason = reason;
        }

        public String getName() {
            return name;
        }

        public String getStatus() {
            return status;
        }

        public String getResolution() {
            return resolution;
        }

        public String getReason() {
            return reason;
        }
    }

    static class ShipmentInfo {
        @JsonProperty("type")
        private final String type;
        @JsonProperty("trackingUrl")
        private final String trackingUrl;
        @JsonProperty("externalId")
        private final String externalId;
        @JsonProperty("carrier")
        private final String carrier;

        ShipmentInfo(Shipment shipment) {
            this.type = shipment.getType() != null ? shipment.getType().name() : "-";
            this.trackingUrl = shipment.getTrackingUrl();
            this.externalId = shipment.getExternalId();
            this.carrier = shipment.getCarrier();
        }

        public String getType() {
            return type;
        }

        public String getTrackingUrl() {
            return trackingUrl;
        }

        public String getExternalId() {
            return externalId;
        }

        public String getCarrier() {
            return carrier;
        }
    }
}
