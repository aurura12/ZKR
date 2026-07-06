CREATE TABLE contract_ledger (
    id              BIGSERIAL PRIMARY KEY,
    expense_id      BIGINT NOT NULL,

    company         VARCHAR(150),
    contract_no     VARCHAR(100),
    signing_entity  VARCHAR(200),
    counterparty    VARCHAR(200),
    description     VARCHAR(500),
    sign_type       VARCHAR(50),
    sign_year       INTEGER,
    sign_month      INTEGER,
    sign_date       DATE,
    contract_amount NUMERIC(15,2),
    currency        VARCHAR(10),
    payment_method  VARCHAR(200),
    start_date      DATE,
    end_date        DATE,
    collection_date DATE,
    status          VARCHAR(50),
    collected_amount NUMERIC(15,2),
    uncollected_amount NUMERIC(15,2),
    invoice_status  VARCHAR(100),
    invoice_amount  NUMERIC(15,2),
    responsible_person VARCHAR(100),
    archive_no      VARCHAR(100),
    remarks         TEXT,

    ocr_status      VARCHAR(20) DEFAULT 'PENDING',
    ocr_raw_json    TEXT,
    ocr_confidence  NUMERIC(5,4),
    ocr_at          TIMESTAMPTZ,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_cl_expense_id ON contract_ledger(expense_id);
CREATE INDEX idx_cl_contract_no ON contract_ledger(contract_no);
CREATE INDEX idx_cl_ocr_status ON contract_ledger(ocr_status);
