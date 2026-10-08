package pl.commercelink.shipping;

import pl.commercelink.orders.ShippingForm;
import pl.commercelink.shipping.api.PackageOption;
import pl.commercelink.shipping.api.ShipmentProposal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * The Wysyłam z Allegro form against the limits of the buyer's delivery method (the proposal): one complete parcel
 * within the dimensions and weight, cash on delivery and insurance within the method's maximum, insurance at least
 * the cash on delivery. Checked before Allegro is asked, so the operator fixes the field instead of reading a refusal.
 * The adapter checks the same before it sends the command; this check only puts the reasons next to the fields.
 */
public final class AllegroShipmentFormCheck {

    /** field: "parcel", "cashOnDeliveryAmount" or "insurance"; key and args: the message shown next to it. */
    public record Problem(String field, String key, Object[] args) {
    }

    private AllegroShipmentFormCheck() {
    }

    public static List<Problem> check(ShippingForm form, ShipmentProposal proposal) {
        List<Problem> problems = new ArrayList<>();
        List<ParcelForm> parcels = form.getCompleteParcels();
        if (parcels.isEmpty()) {
            problems.add(new Problem("parcel", "shipping.allegro.error.parcel", new Object[0]));
            return problems;
        }
        if (parcels.size() > 1) {
            problems.add(new Problem("parcel", "shipping.allegro.error.oneParcel", new Object[0]));
            return problems;
        }
        ParcelForm parcel = parcels.get(0);
        PackageOption option = packageOption(proposal);
        if (option != null) {
            if (exceeds(parcel.getDepth(), option.maxLength()) || exceeds(parcel.getWidth(), option.maxWidth())
                    || exceeds(parcel.getHeight(), option.maxHeight())) {
                problems.add(new Problem("parcel", "shipping.allegro.error.dimensions", new Object[]{
                        plain(option.maxLength()), plain(option.maxWidth()), plain(option.maxHeight())}));
            }
            if (exceeds(parcel.getWeight(), option.maxWeight())) {
                problems.add(new Problem("parcel", "shipping.allegro.error.weight", new Object[]{plain(option.maxWeight())}));
            }
        }
        BigDecimal cod = form.isCashOnDelivery() ? BigDecimal.valueOf(form.getCashOnDeliveryAmount()) : BigDecimal.ZERO;
        if (proposal.maxCashOnDelivery() != null && cod.compareTo(proposal.maxCashOnDelivery()) > 0) {
            problems.add(new Problem("cashOnDeliveryAmount", "shipping.allegro.error.cod",
                    new Object[]{plain(proposal.maxCashOnDelivery())}));
        }
        BigDecimal insurance = BigDecimal.valueOf(parcel.getValue());
        // the insured value is whole zloty (ParcelForm#value): at least the cash on delivery rounded up
        BigDecimal minimum = cod.setScale(0, RoundingMode.CEILING);
        if (insurance.compareTo(minimum) < 0) {
            problems.add(new Problem("insurance", "shipping.allegro.error.insurance.min", new Object[]{plain(minimum)}));
        } else if (proposal.maxInsurance() != null && insurance.compareTo(proposal.maxInsurance()) > 0) {
            problems.add(new Problem("insurance", "shipping.allegro.error.insurance.max",
                    new Object[]{plain(proposal.maxInsurance())}));
        }
        return problems;
    }

    /** The limits of a plain parcel, else of the first option the method has; null when it gives none. */
    public static PackageOption packageOption(ShipmentProposal proposal) {
        List<PackageOption> options = proposal.packageOptions() == null ? List.of() : proposal.packageOptions();
        return options.stream().filter(o -> "PACKAGE".equals(o.type())).findFirst()
                .orElse(options.isEmpty() ? null : options.get(0));
    }

    static String plain(BigDecimal value) {
        return value == null ? "–" : value.stripTrailingZeros().toPlainString();
    }

    private static boolean exceeds(int value, BigDecimal max) {
        return max != null && BigDecimal.valueOf(value).compareTo(max) > 0;
    }
}
