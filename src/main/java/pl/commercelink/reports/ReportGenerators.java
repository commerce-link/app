package pl.commercelink.reports;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
class ReportGenerators {

    private final Map<ReportType, ReportGenerator> byType = new EnumMap<>(ReportType.class);

    ReportGenerators(List<ReportGenerator> generators) {
        generators.forEach(generator -> byType.put(generator.type(), generator));
    }

    ReportGenerator forType(ReportType type) {
        ReportGenerator generator = byType.get(type);
        if (generator == null) {
            throw new IllegalStateException("No generator of the report " + type);
        }
        return generator;
    }
}
