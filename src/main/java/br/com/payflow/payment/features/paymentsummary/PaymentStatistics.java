package br.com.payflow.payment.features.paymentsummary;

// Output port used only by the summary slice, computed by the database for the query's merchant.
public interface PaymentStatistics {

    PaymentSummary summarize(PaymentSummaryQuery query);
}
