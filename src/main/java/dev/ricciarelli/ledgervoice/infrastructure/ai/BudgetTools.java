package dev.ricciarelli.ledgervoice.infrastructure.ai;

import dev.ricciarelli.ledgervoice.application.AssistantResult;
import dev.ricciarelli.ledgervoice.application.LedgerService;
import dev.ricciarelli.ledgervoice.domain.*;
import org.springframework.ai.tool.annotation.Tool;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/** One instance per command. No confirmation or deletion capability is exposed to the model. */
public class BudgetTools {
    private final LedgerService ledger;
    private final CommandSource source;
    private final List<Proposal> proposals = new ArrayList<>();
    private MonthlySummary summary;
    private TransactionPage transactions;
    private String error;
    private int calls;

    public BudgetTools(LedgerService ledger, CommandSource source) {
        this.ledger = ledger;
        this.source = source;
    }

    @Tool(description = "Prepare ONE proposal for review. No transaction is saved. Direction INCOME/EXPENSE; "
            + "amount decimal string with a dot; category FOOD/HOUSING/TRANSPORT/HEALTH/LEISURE/EDUCATION/SALARY/OTHER; "
            + "description 1-120 characters; occurredOn yyyy-MM-dd. Never invent missing fields.", returnDirect = true)
    public ToolReply prepareTransaction(String direction, String amount, String category, String description, String occurredOn) {
        return execute(() -> {
            var draft = new TransactionDraft(Direction.valueOf(direction.toUpperCase(Locale.ROOT)), new BigDecimal(amount),
                    Category.valueOf(category.toUpperCase(Locale.ROOT)), description, LocalDate.parse(occurredOn));
            var proposal = ledger.prepare(draft, source);
            proposals.add(proposal);
            return proposal;
        });
    }

    @Tool(description = "Read exact income, expense and net totals from confirmed transactions for month yyyy-MM.", returnDirect = true)
    public ToolReply monthlySummary(String month) {
        return execute(() -> {
            summary = ledger.summary(month);
            return summary;
        });
    }

    @Tool(description = "List up to 20 confirmed transactions for month yyyy-MM. category is an enum name or ALL.", returnDirect = true)
    public ToolReply listTransactions(String month, String category) {
        return execute(() -> {
            var filter = "ALL".equalsIgnoreCase(category) ? null : Category.valueOf(category.toUpperCase(Locale.ROOT));
            transactions = ledger.list(month, filter, 0, 20);
            return transactions;
        });
    }

    public AssistantResult result() {
        String answer;
        if (error != null) answer = "Não foi possível concluir: " + error;
        else if (!proposals.isEmpty()) answer = "Proposta pronta para revisão. Confira os dados e confirme; nada entrou no orçamento ainda.";
        else if (summary != null) answer = "Resumo calculado a partir dos lançamentos confirmados. Valores em BRL.";
        else if (transactions != null) answer = "Consulta concluída. Os resultados contêm apenas lançamentos confirmados.";
        else answer = "Informe uma receita ou despesa com valor, categoria, descrição e data, ou peça uma consulta por mês.";
        return new AssistantResult(answer, proposals, summary, transactions);
    }

    private ToolReply execute(Supplier<Object> action) {
        if (++calls > 1) {
            error = "Envie uma operação por comando.";
            return new ToolReply(false, "TOOL_LIMIT_REACHED", null);
        }
        try {
            return new ToolReply(true, "REVIEW_REQUIRED_OR_QUERY_COMPLETE", action.get());
        } catch (BudgetException exception) {
            error = exception.getMessage();
            return new ToolReply(false, exception.code().name(), null);
        } catch (IllegalArgumentException | NullPointerException exception) {
            error = "Há campos ausentes ou em formato inválido. Revise valor, categoria e data.";
            return new ToolReply(false, "INVALID_TRANSACTION", null);
        }
    }

    public record ToolReply(boolean ok, String code, Object data) { }
}
