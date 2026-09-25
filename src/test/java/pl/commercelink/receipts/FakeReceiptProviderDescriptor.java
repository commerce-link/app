package pl.commercelink.receipts;

import pl.commercelink.provider.api.EventBinding;
import pl.commercelink.provider.api.ProviderField;
import pl.commercelink.provider.api.WebhookOutcome;
import pl.commercelink.provider.api.WebhookStatusResponse;
import pl.commercelink.receipts.api.Receipt;
import pl.commercelink.receipts.api.ReceiptProvider;
import pl.commercelink.receipts.api.ReceiptProviderDescriptor;

import java.util.List;
import java.util.Map;

public class FakeReceiptProviderDescriptor implements ReceiptProviderDescriptor {

    public static final String NAME = "test-receipts";
    public static final String TOKEN = "token";
    /** Tests swap the instance handed out by create(). */
    public static volatile FakeReceiptProvider provider = new FakeReceiptProvider();

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String displayName() {
        return "Test receipts";
    }

    @Override
    public List<ProviderField> configurationFields() {
        return List.of(new ProviderField("token", "Token", ProviderField.FieldType.PASSWORD, true, null));
    }

    @Override
    public ReceiptProvider create(Map<String, String> configuration) {
        return provider;
    }

    @Override
    public List<EventBinding<?>> bindings() {
        return List.of(new EventBinding.WebhookBinding<Receipt>("test", (body, context) -> {
            if (!TOKEN.equals(context.providerConfig().get("token"))) {
                return WebhookOutcome.of(null, new WebhookStatusResponse("REJECTED"));
            }
            if (body.isBlank()) {
                return WebhookOutcome.empty();
            }
            String[] parts = body.split(";");   // "key;providerId;STATE"
            Receipt receipt = switch (parts[2]) {
                case "FISCALISED" -> Receipt.fiscalised(parts[0], parts[1],
                        new pl.commercelink.receipts.api.FiscalData(null, null, java.time.Instant.parse("2026-09-23T10:00:00Z")),
                        "https://receipts.test/" + parts[1]);
                default -> Receipt.pending(parts[0], parts[1]);
            };
            return WebhookOutcome.of(receipt, null);
        }));
    }
}
