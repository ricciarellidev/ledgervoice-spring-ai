package dev.ricciarelli.ledgervoice.domain;

import java.math.BigDecimal;

public record MonthlySummary(String month, String currency, BigDecimal income,
                             BigDecimal expenses, BigDecimal net, long transactionCount) { }
