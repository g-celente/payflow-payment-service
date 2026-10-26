package br.com.payflow.payment.infrastructure.messaging;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

// Publishes pending outbox rows and marks them, oldest first. Rows are claimed with UPDLOCK + READPAST,
// so several replicas never publish the same row at the same time. Delivery is at-least-once: if the
// commit fails after the broker accepted a batch, it is sent again and consumers drop it by event id.
public final class OutboxRelay {

    private static final String CLAIM = """
            SELECT TOP (?) id, type, aggregate_id, payload
            FROM outbox_event WITH (UPDLOCK, READPAST, ROWLOCK)
            WHERE published_at IS NULL
            ORDER BY occurred_at""";
    private static final String MARK_PUBLISHED = "UPDATE outbox_event SET published_at = ? WHERE id = ?";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final EventPublisher publisher;
    private final int batchSize;
    private final Clock clock;

    public OutboxRelay(JdbcTemplate jdbc, TransactionTemplate transaction, EventPublisher publisher, int batchSize,
            Clock clock) {
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.publisher = publisher;
        this.batchSize = batchSize;
        this.clock = clock;
    }

    // Publishes one batch; returns how many rows were published. A broker failure rolls back and leaves them pending.
    public int publishPending() {
        Integer published = transaction.execute(status -> {
            List<OutboxMessage> batch = jdbc.query(CLAIM, (row, index) -> new OutboxMessage(
                    UUID.fromString(row.getString("id")), row.getString("type"),
                    UUID.fromString(row.getString("aggregate_id")), row.getString("payload")), batchSize);
            if (batch.isEmpty()) {
                return 0;
            }
            publisher.publish(batch);
            OffsetDateTime now = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
            jdbc.batchUpdate(MARK_PUBLISHED, batch.stream()
                    .map(message -> new Object[]{now, message.id().toString()})
                    .toList());
            return batch.size();
        });
        return published == null ? 0 : published;
    }

    // Drains the backlog in batches, stopping at the first partial batch.
    public int publishAllPending() {
        int total = 0;
        int published;
        do {
            published = publishPending();
            total += published;
        } while (published == batchSize);
        return total;
    }
}
