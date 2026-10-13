CREATE TABLE proposals (
    id VARCHAR(36) PRIMARY KEY,
    direction VARCHAR(7) NOT NULL CHECK (direction IN ('INCOME', 'EXPENSE')),
    amount DECIMAL(14, 2) NOT NULL CHECK (amount > 0 AND amount <= 1000000.00),
    category VARCHAR(16) NOT NULL CHECK (category IN ('FOOD','HOUSING','TRANSPORT','HEALTH','LEISURE','EDUCATION','SALARY','OTHER')),
    description VARCHAR(120) NOT NULL,
    occurred_on DATE NOT NULL,
    source VARCHAR(6) NOT NULL CHECK (source IN ('MANUAL', 'TEXT', 'AUDIO')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    transaction_id VARCHAR(36) UNIQUE
);

CREATE TABLE transactions (
    id VARCHAR(36) PRIMARY KEY,
    proposal_id VARCHAR(36) NOT NULL UNIQUE REFERENCES proposals(id),
    direction VARCHAR(7) NOT NULL CHECK (direction IN ('INCOME', 'EXPENSE')),
    amount DECIMAL(14, 2) NOT NULL CHECK (amount > 0 AND amount <= 1000000.00),
    category VARCHAR(16) NOT NULL CHECK (category IN ('FOOD','HOUSING','TRANSPORT','HEALTH','LEISURE','EDUCATION','SALARY','OTHER')),
    description VARCHAR(120) NOT NULL,
    occurred_on DATE NOT NULL,
    source VARCHAR(6) NOT NULL CHECK (source IN ('MANUAL', 'TEXT', 'AUDIO')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

ALTER TABLE proposals ADD CONSTRAINT fk_confirmed_transaction FOREIGN KEY (transaction_id) REFERENCES transactions(id);
CREATE INDEX idx_transactions_month ON transactions(occurred_on, category);

CREATE TABLE audit_events (
    id VARCHAR(36) PRIMARY KEY,
    proposal_id VARCHAR(36) NOT NULL REFERENCES proposals(id),
    action VARCHAR(24) NOT NULL CHECK (action IN ('PROPOSAL_CREATED', 'TRANSACTION_CONFIRMED', 'CONFIRMATION_REPLAYED')),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_audit_proposal ON audit_events(proposal_id, occurred_at);
