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
import static org.mockito.Mockito.when;

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
        // given
        AtomicReference<String> captured = new AtomicReference<>();
        registry.registerDescriptor(fakeDescriptor("Wortmann", captured));

        // when
        Optional<FeedData> result = registry.downloadFeed("Wortmann", Map.of("url", "https://feed/x.csv"));

        // then
        assertTrue(result.isPresent());
        assertEquals("https://feed/x.csv", captured.get());
    }

    @Test
    void downloadFeedWithConfigEncodesMultiFieldAsJsonAndPassesToDescriptor() throws ResourceDownloadException {
        // given
        AtomicReference<String> captured = new AtomicReference<>();
        registry.registerDescriptor(fakeDescriptor("Action", captured));

        // when
        registry.downloadFeed("Action", Map.of("host", "ftp.x", "password", "p"));

        // then
        assertEquals("{\"host\":\"ftp.x\",\"password\":\"p\"}", captured.get());
    }

    @Test
    void downloadFeedWithConfigReturnsEmptyForUnknownSupplier() throws ResourceDownloadException {
        // when
        Optional<FeedData> result = registry.downloadFeed("Unknown", Map.of("url", "x"));

        // then
        assertTrue(result.isEmpty());
    }

    @Test
    void downloadFeedBySupplierNameLoadsSecretAndDelegatesToDescriptor() throws ResourceDownloadException {
        // given
        AtomicReference<String> captured = new AtomicReference<>();
        registry.registerDescriptor(fakeDescriptor("Kosatec", captured));
        when(secretsManager.getSecret("Kosatec")).thenReturn("stored-secret");

        // when
        Optional<FeedData> result = registry.downloadFeed("Kosatec");

        // then
        assertTrue(result.isPresent());
        assertEquals("stored-secret", captured.get());
    }

    @Test
    void downloadFeedBySupplierNameReturnsEmptyForUnknownSupplier() throws ResourceDownloadException {
        // when
        Optional<FeedData> result = registry.downloadFeed("Unknown");

        // then
        assertTrue(result.isEmpty());
    }
}
