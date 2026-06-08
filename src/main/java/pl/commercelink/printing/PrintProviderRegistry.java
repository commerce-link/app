package pl.commercelink.printing;

import org.springframework.stereotype.Service;
import pl.commercelink.printing.api.PrintProvider;
import pl.commercelink.printing.api.PrintProviderDescriptor;
import pl.commercelink.printing.api.PrintingException;
import pl.commercelink.stores.Printer;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ServiceLoader;

@Service
public class PrintProviderRegistry {

    private final Map<String, PrintProviderDescriptor> descriptors = new LinkedHashMap<>();

    public PrintProviderRegistry() {
        for (PrintProviderDescriptor descriptor : ServiceLoader.load(PrintProviderDescriptor.class)) {
            descriptors.put(descriptor.name(), descriptor);
        }
    }

    public Collection<PrintProviderDescriptor> availableProviders() {
        return descriptors.values();
    }

    public PrintProviderDescriptor getDescriptor(String name) {
        return descriptors.get(name);
    }

    public PrintProvider create(Printer printer) {
        PrintProviderDescriptor descriptor = descriptors.get(printer.getProviderName());
        if (descriptor == null) {
            throw new PrintingException("Unknown print provider: " + printer.getProviderName());
        }
        return descriptor.create(printer.getSettings());
    }
}
