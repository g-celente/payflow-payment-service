-- Payments domain (Azure SQL / SQL Server). Amounts in cents; timestamps in UTC with offset.

CREATE TABLE payment (
    id                UNIQUEIDENTIFIER  NOT NULL CONSTRAINT pk_payment PRIMARY KEY,
    merchant_id       UNIQUEIDENTIFIER  NOT NULL,
    amount_cents      BIGINT            NOT NULL CONSTRAINT ck_payment_amount CHECK (amount_cents > 0),
    currency          CHAR(3)           NOT NULL,
    method            VARCHAR(10)       NOT NULL,
    status            VARCHAR(20)       NOT NULL,
    card_token        VARCHAR(64)       NULL,
    card_last4        CHAR(4)           NULL,
    pix_qr_code       NVARCHAR(512)     NULL,
    gateway_reference VARCHAR(64)       NULL,
    description       NVARCHAR(255)     NULL,
    metadata          NVARCHAR(MAX)     NULL,
    created_at        DATETIMEOFFSET(6) NOT NULL,
    updated_at        DATETIMEOFFSET(6) NOT NULL,
    version           BIGINT            NOT NULL
);

-- Listing, summary and "last payments" of the panel are always per merchant, newest first.
CREATE INDEX ix_payment_merchant_created ON payment (merchant_id, created_at DESC);

CREATE TABLE refund (
    id           UNIQUEIDENTIFIER  NOT NULL CONSTRAINT pk_refund PRIMARY KEY,
    payment_id   UNIQUEIDENTIFIER  NOT NULL CONSTRAINT fk_refund_payment REFERENCES payment (id),
    amount_cents BIGINT            NOT NULL CONSTRAINT ck_refund_amount CHECK (amount_cents > 0),
    reason       NVARCHAR(255)     NULL,
    created_at   DATETIMEOFFSET(6) NOT NULL
);

CREATE INDEX ix_refund_payment ON refund (payment_id);

-- One row per (merchant, Idempotency-Key). response_status NULL = request still being processed.
CREATE TABLE idempotency_key (
    merchant_id     UNIQUEIDENTIFIER  NOT NULL,
    idem_key        NVARCHAR(255)     NOT NULL,
    request_hash    CHAR(64)          NOT NULL,
    response_status INT               NULL,
    response_body   NVARCHAR(MAX)     NULL,
    created_at      DATETIMEOFFSET(6) NOT NULL,
    expires_at      DATETIMEOFFSET(6) NOT NULL,
    CONSTRAINT pk_idempotency_key PRIMARY KEY (merchant_id, idem_key)
);

-- Transactional outbox: written with the payment, published to Service Bus by the OutboxRelay.
CREATE TABLE outbox_event (
    id           UNIQUEIDENTIFIER  NOT NULL CONSTRAINT pk_outbox_event PRIMARY KEY,
    aggregate_id UNIQUEIDENTIFIER  NOT NULL,
    type         VARCHAR(50)       NOT NULL,
    payload      NVARCHAR(MAX)     NOT NULL,
    occurred_at  DATETIMEOFFSET(6) NOT NULL,
    published_at DATETIMEOFFSET(6) NULL
);

CREATE INDEX ix_outbox_event_pending ON outbox_event (occurred_at) WHERE published_at IS NULL;
