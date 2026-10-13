package dev.ricciarelli.ledgervoice.web;

import dev.ricciarelli.ledgervoice.application.LedgerService;
import dev.ricciarelli.ledgervoice.domain.*;
import dev.ricciarelli.ledgervoice.web.dto.DraftRequest;
import dev.ricciarelli.ledgervoice.web.dto.ProposalResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class LedgerController {
    private final LedgerService ledger;
    private final Clock clock;

    public LedgerController(LedgerService ledger, Clock clock) {
        this.ledger = ledger;
        this.clock = clock;
    }

    @PostMapping("/proposals")
    public ResponseEntity<ProposalResponse> prepare(@Valid @RequestBody DraftRequest request) {
        var proposal = ledger.prepare(request.toDraft(), CommandSource.MANUAL);
        return ResponseEntity.created(URI.create("/api/v1/proposals/" + proposal.id()))
                .body(ProposalResponse.from(proposal, Instant.now(clock)));
    }

    @GetMapping("/proposals/{id}")
    public ProposalResponse proposal(@PathVariable UUID id) {
        return ProposalResponse.from(ledger.findProposal(id), Instant.now(clock));
    }

    @PostMapping("/proposals/{id}/confirm")
    public ResponseEntity<Transaction> confirm(@PathVariable UUID id) {
        var result = ledger.confirm(id);
        var response = result.replayed() ? ResponseEntity.ok()
                : ResponseEntity.created(URI.create("/api/v1/transactions/" + result.transaction().id()));
        return response.header("Confirmation-Replayed", Boolean.toString(result.replayed())).body(result.transaction());
    }

    @GetMapping("/proposals/{id}/audit")
    public List<AuditEvent> audit(@PathVariable UUID id) {
        return ledger.auditFor(id);
    }

    @GetMapping("/transactions/{id}")
    public Transaction transaction(@PathVariable UUID id) {
        return ledger.findTransaction(id);
    }

    @GetMapping("/transactions")
    public TransactionPage transactions(@RequestParam(required = false) String month,
                                        @RequestParam(required = false) Category category,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        return ledger.list(month, category, page, size);
    }

    @GetMapping("/summary")
    public MonthlySummary summary(@RequestParam(required = false) String month) {
        return ledger.summary(month);
    }
}
