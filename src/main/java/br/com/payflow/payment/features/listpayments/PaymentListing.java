package br.com.payflow.payment.features.listpayments;

// Output port used only by the list slice. Newest payments first, only the query's merchant.
public interface PaymentListing {

    PaymentPage list(ListPaymentsQuery query);
}
