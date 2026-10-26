package br.com.payflow.payment.infrastructure.persistence;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import br.com.payflow.payment.domain.ConcurrentPaymentUpdateException;
import br.com.payflow.payment.domain.Money;
import br.com.payflow.payment.domain.Payment;
import br.com.payflow.payment.domain.PaymentMethod;
import br.com.payflow.payment.domain.PaymentNotFoundException;
import br.com.payflow.payment.domain.PaymentStatus;
import br.com.payflow.payment.features.cancelpayment.PaymentCancellationStore;
import br.com.payflow.payment.features.confirmpixpayment.PixConfirmationStore;
import br.com.payflow.payment.features.createpayment.NewPaymentStore;
import br.com.payflow.payment.features.getpayment.PaymentReader;
import br.com.payflow.payment.features.listpayments.ListPaymentsQuery;
import br.com.payflow.payment.features.listpayments.PaymentListing;
import br.com.payflow.payment.features.listpayments.PaymentPage;
import br.com.payflow.payment.features.paymentsummary.PaymentStatistics;
import br.com.payflow.payment.features.paymentsummary.PaymentSummary;
import br.com.payflow.payment.features.paymentsummary.PaymentSummaryQuery;
import br.com.payflow.payment.features.refundpayment.PaymentRefundStore;
import br.com.payflow.payment.features.updatepayment.PaymentDetailsStore;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

// One JPA adapter for the payment tables, exposed to each slice through its own port.
// Writes go with their outbox events in the same transaction.
public class JpaPaymentStore implements NewPaymentStore, PaymentListing, PaymentReader, PaymentDetailsStore,
        PaymentCancellationStore, PaymentRefundStore, PixConfirmationStore, PaymentStatistics {

    // Statuses whose amount counts as approved volume (refunds are subtracted).
    private static final List<PaymentStatus> VOLUME = List.of(PaymentStatus.AUTHORIZED, PaymentStatus.CONFIRMED,
            PaymentStatus.PARTIALLY_REFUNDED, PaymentStatus.REFUNDED);

    private final PaymentJpaRepository repository;
    private final EntityManager entityManager;
    private final PaymentEntityMapper mapper;
    private final CloudEventFactory cloudEvents;

    public JpaPaymentStore(PaymentJpaRepository repository, EntityManager entityManager, JsonMapper json) {
        this.repository = repository;
        this.entityManager = entityManager;
        this.mapper = new PaymentEntityMapper(json);
        this.cloudEvents = new CloudEventFactory(json);
    }

    @Override
    @Transactional
    public void add(Payment payment) {
        entityManager.persist(mapper.toNewEntity(payment));
        writeEvents(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Payment> findById(UUID merchantId, UUID paymentId) {
        return repository.findByIdAndMerchantId(paymentId, merchantId).map(mapper::toDomain);
    }

    @Override
    @Transactional
    public void save(Payment payment) {
        PaymentEntity entity = repository.findByIdAndMerchantId(payment.id(), payment.merchantId())
                .orElseThrow(() -> new PaymentNotFoundException(payment.id()));
        if (entity.version != payment.version()) {
            throw new ConcurrentPaymentUpdateException(payment.id());
        }
        mapper.applyChanges(payment, entity);
        writeEvents(payment);
        try {
            // The UPDATE carries "where version = ?": a concurrent change fails here instead of being overwritten.
            entityManager.flush();
        } catch (OptimisticLockException | ObjectOptimisticLockingFailureException exception) {
            throw new ConcurrentPaymentUpdateException(payment.id());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentPage list(ListPaymentsQuery query) {
        Specification<PaymentEntity> filter = (root, criteria, builder) -> builder.and(
                listFilter(builder, root, query).toArray(Predicate[]::new));
        Page<PaymentEntity> page = repository.findAll(filter,
                PageRequest.of(query.page(), query.size(), Sort.by(Sort.Direction.DESC, "createdAt")));
        return new PaymentPage(page.getContent().stream().map(mapper::toDomain).toList(), query.page(), query.size(),
                page.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentSummary summarize(PaymentSummaryQuery query) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();

        CriteriaQuery<Tuple> byStatusQuery = builder.createTupleQuery();
        Root<PaymentEntity> payment = byStatusQuery.from(PaymentEntity.class);
        byStatusQuery.multiselect(payment.get("status"), builder.count(payment))
                .where(period(builder, payment, query).toArray(Predicate[]::new))
                .groupBy(payment.get("status"));
        Map<PaymentStatus, Long> byStatus = new EnumMap<>(PaymentStatus.class);
        long count = 0;
        for (Tuple row : entityManager.createQuery(byStatusQuery).getResultList()) {
            long statusCount = row.get(1, Long.class);
            byStatus.put(row.get(0, PaymentStatus.class), statusCount);
            count += statusCount;
        }

        CriteriaQuery<Tuple> grossQuery = builder.createTupleQuery();
        Root<PaymentEntity> approved = grossQuery.from(PaymentEntity.class);
        List<Predicate> approvedFilter = period(builder, approved, query);
        approvedFilter.add(approved.get("status").in(VOLUME));
        grossQuery.multiselect(approved.get("method"), builder.sum(approved.<Long>get("amountCents")))
                .where(approvedFilter.toArray(Predicate[]::new))
                .groupBy(approved.get("method"));

        CriteriaQuery<Tuple> refundedQuery = builder.createTupleQuery();
        Root<RefundEntity> refund = refundedQuery.from(RefundEntity.class);
        Join<RefundEntity, PaymentEntity> refunded = refund.join("payment");
        refundedQuery.multiselect(refunded.get("method"), builder.sum(refund.<Long>get("amountCents")))
                .where(period(builder, refunded, query).toArray(Predicate[]::new))
                .groupBy(refunded.get("method"));

        Map<PaymentMethod, Long> byMethod = new EnumMap<>(PaymentMethod.class);
        for (Tuple row : entityManager.createQuery(grossQuery).getResultList()) {
            byMethod.merge(row.get(0, PaymentMethod.class), row.get(1, Long.class), Long::sum);
        }
        for (Tuple row : entityManager.createQuery(refundedQuery).getResultList()) {
            byMethod.merge(row.get(0, PaymentMethod.class), -row.get(1, Long.class), Long::sum);
        }
        long total = byMethod.values().stream().mapToLong(Long::longValue).sum();
        return new PaymentSummary(Money.BRL, total, count, byStatus, byMethod);
    }

    private void writeEvents(Payment payment) {
        for (var event : payment.pullEvents()) {
            entityManager.persist(new OutboxEventEntity(event.id(), event.paymentId(), event.type().wireName(),
                    cloudEvents.toJson(event), event.occurredAt()));
        }
    }

    private static List<Predicate> period(CriteriaBuilder builder, From<?, PaymentEntity> payment,
            PaymentSummaryQuery query) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(builder.equal(payment.get("merchantId"), query.merchantId()));
        if (query.from() != null) {
            predicates.add(builder.greaterThanOrEqualTo(payment.get("createdAt"), query.from()));
        }
        if (query.to() != null) {
            predicates.add(builder.lessThan(payment.get("createdAt"), query.to()));
        }
        return predicates;
    }

    private static List<Predicate> listFilter(CriteriaBuilder builder, From<?, PaymentEntity> payment,
            ListPaymentsQuery query) {
        List<Predicate> predicates = period(builder, payment, new PaymentSummaryQuery(query.merchantId(), query.from(), query.to()));
        if (query.status() != null) {
            predicates.add(builder.equal(payment.get("status"), query.status()));
        }
        if (query.method() != null) {
            predicates.add(builder.equal(payment.get("method"), query.method()));
        }
        return predicates;
    }
}
