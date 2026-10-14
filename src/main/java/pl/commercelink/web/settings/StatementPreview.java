package pl.commercelink.web.settings;

import org.springframework.context.MessageSource;
import pl.commercelink.payments.statements.BankOperation;
import pl.commercelink.payments.statements.BankStatementParser;
import pl.commercelink.payments.statements.ParsedStatement;
import pl.commercelink.payments.statements.StatementFileException;
import pl.commercelink.stores.BankStatementFormat;
import pl.commercelink.web.orders.Money;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * The sample file read with the format as typed, under the format form: the first operations as the reconciliation
 * will see them, how many were read, and the rows that could not be. error is set (translated) when the file cannot be
 * read at all with these settings, e.g. a column that is not in the header.
 */
public record StatementPreview(String fileName, List<String> headers, List<Row> rows, int operations, long incoming,
                               long outgoing, int skipped, List<String> problems, String error) {

    private static final int ROWS = 8;
    private static final int PROBLEMS = 5;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public record Row(String date, boolean incoming, String amount, String title, String sender, String number) {
    }

    public static StatementPreview of(byte[] sample, String fileName, BankStatementFormat format, String accountKey,
                                      MessageSource messages, Locale locale) {
        List<String> headers;
        try {
            headers = BankStatementParser.headers(sample, format);
        } catch (StatementFileException e) {
            return failed(fileName, List.of(), e, messages, locale);
        }
        if (!format.isComplete()) {
            return new StatementPreview(fileName, headers, List.of(), 0, 0, 0, 0, List.of(),
                    messages.getMessage("store.payments.statement.preview.incomplete", null, locale));
        }
        ParsedStatement statement;
        try {
            statement = BankStatementParser.parse(sample, format, accountKey);
        } catch (StatementFileException e) {
            return failed(fileName, headers, e, messages, locale);
        }
        List<Row> rows = statement.operations().stream().limit(ROWS).map(StatementPreview::row).toList();
        List<String> problems = statement.problems().stream().limit(PROBLEMS)
                .map(problem -> messages.getMessage("store.payments.statement.preview.problem." + problem.problem().name(),
                        new Object[]{problem.line()}, locale))
                .toList();
        return new StatementPreview(fileName, statement.headers(), rows, statement.operations().size(),
                statement.incoming().size(), statement.outgoingCount(), statement.skippedRows(), problems,
                statement.operations().isEmpty()
                        ? messages.getMessage("store.payments.statement.preview.empty", null, locale) : null);
    }

    private static Row row(BankOperation operation) {
        return new Row(operation.date().format(DAY), operation.isIncoming(), Money.format(operation.amount().doubleValue()),
                operation.title(), operation.sender(), operation.number());
    }

    private static StatementPreview failed(String fileName, List<String> headers, StatementFileException e,
                                           MessageSource messages, Locale locale) {
        return new StatementPreview(fileName, headers, List.of(), 0, 0, 0, 0, List.of(),
                messages.getMessage(e.getMessageKey(), e.getArguments(), locale));
    }
}
