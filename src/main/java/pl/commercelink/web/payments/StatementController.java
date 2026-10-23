package pl.commercelink.web.payments;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.payments.statements.Allocation;
import pl.commercelink.payments.statements.BankOperation;
import pl.commercelink.payments.statements.BankStatementParser;
import pl.commercelink.payments.statements.ParsedStatement;
import pl.commercelink.payments.statements.PayoutChannels;
import pl.commercelink.payments.statements.StatementBooking;
import pl.commercelink.payments.statements.StatementFileException;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.BankAccount;
import pl.commercelink.stores.PayoutChannel;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Bank statements on the Payments page: an operator uploads the CSV of one account, reconciles its incoming transfers
 * with the open orders and books the ones they accept. Neither the file nor the page's state is stored: the upload is
 * read once, the operations travel with the page, and a booking that is repeated (a statement uploaded again) is
 * recognised by the operation's number on the orders' payments.
 */
@Controller
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class StatementController {

    public static final String PATH = PaymentsQuery.PATH + "/statement";
    private static final String VIEW = "payments-statement";
    private static final String UPLOAD_VIEW = "payments-statement-upload";

    private final StoresRepository storesRepository;
    private final StatementPageFactory pageFactory;
    private final StatementBooking booking;
    private final StatementJson json;
    private final MessageSource messageSource;

    /** The upload form as a page, for a browser without the Payments page's dialog. */
    @GetMapping(PATH)
    public String uploadForm(Model model) {
        Store store = requireStore();
        model.addAttribute("statementAccounts", StatementAccounts.of(store));
        return UPLOAD_VIEW;
    }

    @PostMapping(PATH)
    public String upload(@RequestParam(required = false) String accountId,
                         @RequestParam(required = false) MultipartFile file,
                         Model model, Locale locale, RedirectAttributes redirectAttributes) {
        Store store = requireStore();
        BankAccount account = store.findBankAccount(StringUtils.defaultString(accountId))
                .filter(BankAccount::hasStatementFormat)
                .orElse(null);
        if (account == null) {
            return refuse(redirectAttributes, "payments.statement.error.account", locale);
        }
        if (file == null || file.isEmpty()) {
            return refuse(redirectAttributes, "payments.statement.error.file", locale);
        }
        ParsedStatement statement;
        try {
            statement = BankStatementParser.parse(file.getBytes(), account.getStatementFormat(), account.getIban());
        } catch (StatementFileException e) {
            return refuse(redirectAttributes, messageSource.getMessage(e.getMessageKey(), e.getArguments(), locale));
        } catch (IOException e) {
            return refuse(redirectAttributes, "payments.statement.error.file", locale);
        }
        if (statement.incoming().isEmpty()) {
            return refuse(redirectAttributes, "payments.statement.error.noIncoming", locale);
        }
        List<String> problems = statement.problems().stream()
                .map(problem -> messageSource.getMessage("store.payments.statement.preview.problem." + problem.problem().name(),
                        new Object[]{problem.line()}, locale))
                .toList();
        StatementPageFactory.Statement read = new StatementPageFactory.Statement(account,
                StringUtils.defaultString(file.getOriginalFilename()), statement.incoming(),
                (int) statement.outgoingCount(), statement.skippedRows(), problems);
        model.addAttribute("page", pageFactory.page(store, read, Map.of(), List.of(), locale));
        return VIEW;
    }

    /**
     * Books the ticked operations with the orders the page holds for them, each through {@link StatementBooking}, then
     * shows the statement again: what was booked, what was not and why, and the operations still waiting.
     */
    @PostMapping(PATH + "/book")
    public String book(@RequestParam(required = false) String accountId,
                       @RequestParam(required = false) String fileName,
                       @RequestParam(required = false) String operations,
                       @RequestParam(required = false) String allocations,
                       @RequestParam(required = false) List<String> selected,
                       @RequestParam(defaultValue = "0") int outgoing,
                       @RequestParam(defaultValue = "0") int skipped,
                       @RequestParam(required = false) String problems,
                       Model model, Locale locale, RedirectAttributes redirectAttributes) {
        Store store = requireStore();
        BankAccount account = store.findBankAccount(StringUtils.defaultString(accountId))
                .filter(BankAccount::hasStatementFormat)
                .orElse(null);
        List<BankOperation> incoming = json.operations(operations).stream().filter(BankOperation::isIncoming).toList();
        if (account == null || incoming.isEmpty()) {
            return refuse(redirectAttributes, "payments.statement.error.expired", locale);
        }
        Map<String, List<Allocation>> chosen = json.allocations(allocations);
        Map<String, BankOperation> byNumber = incoming.stream()
                .collect(Collectors.toMap(BankOperation::number, Function.identity(), (first, second) -> first, LinkedHashMap::new));
        List<PayoutChannel> channels = account.getStatementFormat().payoutChannelsOrEmpty();
        List<StatementBooking.Outcome> outcomes = new ArrayList<>();
        for (String number : selected == null ? List.<String>of() : selected.stream().distinct().toList()) {
            BankOperation operation = byNumber.get(number);
            List<Allocation> orders = chosen.get(number);
            if (operation == null || orders == null || orders.isEmpty()) {
                continue;
            }
            // whether fees may be booked is the format's call, not the page's
            outcomes.add(booking.book(store.getStoreId(), operation, orders, PayoutChannels.payer(operation, channels)));
        }
        Map<String, List<Allocation>> kept = new LinkedHashMap<>(chosen);
        outcomes.stream().filter(StatementBooking.Outcome::booked).forEach(outcome -> kept.remove(outcome.operation().number()));
        StatementPageFactory.Statement read = new StatementPageFactory.Statement(account, StringUtils.defaultString(fileName),
                incoming, outgoing, skipped, json.strings(problems));
        model.addAttribute("page", pageFactory.page(store, read, kept, outcomes, locale));
        return VIEW;
    }

    private String refuse(RedirectAttributes redirectAttributes, String key, Locale locale) {
        return refuse(redirectAttributes, messageSource.getMessage(key, null, locale));
    }

    private String refuse(RedirectAttributes redirectAttributes, String message) {
        redirectAttributes.addFlashAttribute(PaymentsReturn.ERROR, message);
        return "redirect:" + PaymentsQuery.PATH;
    }

    private Store requireStore() {
        Store store = storesRepository.findById(CustomSecurityContext.getStoreId());
        if (store == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return store;
    }
}
