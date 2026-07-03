CREATE TABLE invoice_ledger (
    seq_no          BIGSERIAL PRIMARY KEY,
    expense_id      BIGINT NOT NULL,
    excel_row       INTEGER,
    image_file      VARCHAR(500),
    image_hash      VARCHAR(64),

    company         VARCHAR(150),
    company_code    VARCHAR(50),
    year            INTEGER,
    month           INTEGER,
    expense_date    DATE,
    tracking_no     VARCHAR(100),
    expense_nature  VARCHAR(100),
    amount_ex_tax   NUMERIC(15,2),
    tax_amount      NUMERIC(15,2),
    tax_rate        VARCHAR(20),
    amount_incl_tax NUMERIC(15,2),
    summary         VARCHAR(500),
    invoice_number  VARCHAR(100),
    is_project_rel  BOOLEAN DEFAULT TRUE,
    project_name    VARCHAR(150),
    project_support VARCHAR(500),
    counterparty    VARCHAR(200),
    contract_no     VARCHAR(100),

    data_source     VARCHAR(20) DEFAULT 'EXCEL',
    ocr_status      VARCHAR(20) DEFAULT 'PENDING',
    ocr_raw_json    TEXT,
    ocr_confidence  NUMERIC(5,4),
    ocr_at          TIMESTAMPTZ,
    verified_status VARCHAR(20) DEFAULT 'PENDING',
    verified_by     VARCHAR(64),
    verified_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_il_expense_id ON invoice_ledger(expense_id);
CREATE INDEX idx_il_tracking_no ON invoice_ledger(tracking_no);
CREATE INDEX idx_il_ocr_status ON invoice_ledger(ocr_status);
