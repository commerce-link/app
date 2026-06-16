package pl.commercelink.inventory.supplier;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.supplier.api.FeedData;
import pl.commercelink.inventory.supplier.api.FeedFormat;
import pl.commercelink.inventory.supplier.api.SupplierDescriptor;
import pl.commercelink.inventory.supplier.api.SupplierInfo;
import pl.commercelink.inventory.supplier.api.SupplierType;
import pl.commercelink.inventory.supplier.api.ShippingPolicy;
import pl.commercelink.inventory.supplier.api.ShippingTerms;
import pl.commercelink.inventory.supplier.api.ShippingCostPolicy;
import pl.commercelink.starter.secrets.SecretsManager;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import pl.commercelink.inventory.supplier.api.support.ResourceDownloadException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class SupplierRegistryDownloadFeedTest {

    private final SecretsManager secretsManager = mock(SecretsManager.class);
    private final SupplierSecretCodec codec = new SupplierSecretCodec();
    private final SupplierRegistry registry = new SupplierRegistry(secretsManager, codec);

    private SupplierDescriptor fakeDescriptor(String name, AtomicReference<String> capturedSecret) {
        return new SupplierDescriptor() {
            @Override
            public Optional<FeedData> download(String secret) {
                capturedSecret.set(secret);
                return Optional.of(FeedData.csv("rows".getBytes()));
            }

            @Override
            public FeedFormat feedFormat() {
                return new FeedFormat.Csv(mock(pl.commercelink.inventory.supplier.api.CsvRowParser.class), ';');
            }

            @Override
            public SupplierInfo supplierInfo() {
                return new SupplierInfo(name, SupplierType.Distributor, 1, "PL",
                        new ShippingPolicy(new ShippingTerms(1, new ShippingCostPolicy.Free())));
            }
        };
    }

    @Test
    void downloadFeedWithConfigEncodesSingleFieldAsRawValueAndPassesToDescriptor() throws ResourceDownloadException {
        AtomicReference<String> captured = new AtomicReference<>();
        registry.registerDescriptor(fakeDescriptor("Wortmann", captured));

        Optional<FeedData> result = registry.downloadFeed("Wortmann", Map.of("url", "https://feed/x.csv"));

        assertTrue(result.isPresent());
        assertEquals("https://feed/x.csv", captured.get());
    }

    @Test
    void downloadFeedWithConfigEncodesMultiFieldAsJsonAndPassesToDescriptor() throws ResourceDownloadException {
        AtomicReference<String> captured = new AtomicReference<>();
        registry.registerDescriptor(fakeDescriptor("Action", captured));

        registry.downloadFeed("Action", Map.of("host", "ftp.x", "password", "p"));

        assertTrue(captured.get().contains("\"host\":\"ftp.x\""));
        assertTrue(captured.get().contains("\"password\":\"p\""));
    }

    @Test
    void downloadFeedWithConfigReturnsEmptyForUnknownSupplier() throws ResourceDownloadException {
        Optional<FeedData> result = registry.downloadFeed("Unknown", Map.of("url", "x"));
        assertTrue(result.isEmpty());
    }
}
