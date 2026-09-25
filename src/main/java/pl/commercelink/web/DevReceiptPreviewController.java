package pl.commercelink.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;
import pl.commercelink.demo.DemoStoreSeeder;
import pl.commercelink.receipts.ReceiptAttempt;
import pl.commercelink.receipts.ReceiptAttemptStore;
import pl.commercelink.receipts.ReceiptProviderFactory;
import pl.commercelink.receipts.ReceiptSnapshotJson;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

/**
 * Preview of an e-receipt issued by the in-memory {@code receipts-dev} simulator, which has no document of its own: the
 * demo store points the simulator's {@code documentUrlBase} here, so the receipt link on the order and in the customer
 * e-mail opens a page instead of a dead host. It exists only while the simulator is on the classpath (the {@code dev}
 * Maven profile); every other build answers 404. The page is rendered from the attempt's frozen request and is
 * scoped to the logged-in store like the order pages.
 */
@Controller
@RequiredArgsConstructor
public class DevReceiptPreviewController {

    /** Where the demo store's {@code receipts-dev} links point; the provider's receipt id is appended. */
    public static final String PATH_PREFIX = "/dashboard/dev-receipts/";

    private final ReceiptProviderFactory receiptProviderFactory;
    private final ReceiptAttemptStore attemptStore;
    private final StoresRepository storesRepository;

    @GetMapping(PATH_PREFIX + "{providerReceiptId}")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String preview(@PathVariable String providerReceiptId, Model model) {
        if (receiptProviderFactory.getDescriptor(DemoStoreSeeder.DEV_RECEIPTS) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        String storeId = CustomSecurityContext.getStoreId();
        if (storeId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        ReceiptAttempt attempt = attemptStore.findByProviderReceiptId(storeId, providerReceiptId)
                .filter(a -> a.getRequestSnapshot() != null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Store store = storesRepository.findById(storeId);
        model.addAttribute("receipt", DevReceiptPreview.of(attempt, ReceiptSnapshotJson.read(attempt.getRequestSnapshot()),
                store == null ? null : store.getName()));
        return "dev-receipt";
    }
}
