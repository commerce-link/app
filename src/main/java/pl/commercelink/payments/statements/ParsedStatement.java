package pl.commercelink.payments.statements;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * A statement read with its account's format: the header cells (for the settings page), every operation, the rows that
 * looked like operations but could not be read, and how many rows were left out as not being operations at all (empty
 * lines, a closing balance, operations of 0).
 */
public record ParsedStatement(List<String> headers, List<BankOperation> operations, List<RowProblem> problems,
                              int skippedRows) {

    public enum Problem { DATE, AMOUNT }

    public record RowProblem(int line, Problem problem) {
    }

    public List<BankOperation> incoming() {
        return operations.stream().filter(BankOperation::isIncoming).toList();
    }

    public long outgoingCount() {
        return operations.stream().filter(operation -> !operation.isIncoming()).count();
    }

    public Optional<LocalDate> firstDate() {
        return operations.stream().map(BankOperation::date).min(Comparator.naturalOrder());
    }

    public Optional<LocalDate> lastDate() {
        return operations.stream().map(BankOperation::date).max(Comparator.naturalOrder());
    }
}
