package dev.ricciarelli.ledgervoice.domain;

import java.util.List;

public record TransactionPage(List<Transaction> items, long total, int page, int size) {
    public TransactionPage {
        items = List.copyOf(items);
    }
}
