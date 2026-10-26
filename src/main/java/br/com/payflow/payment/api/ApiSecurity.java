package br.com.payflow.payment.api;

// Header names and the OpenAPI security scheme shared by the endpoints, the filters and the Swagger setup.
public final class ApiSecurity {

    // Every /v1 route is called by the BFF only, with the shared internal key...
    public static final String INTERNAL_KEY_HEADER = "X-Internal-Key";
    // ...and the merchant the BFF already authenticated. Payments of other merchants answer 404.
    public static final String MERCHANT_ID_HEADER = "X-Merchant-Id";
    // Required on POST /v1/payments and POST /v1/payments/{id}/refunds.
    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    public static final String INTERNAL_KEY_SCHEME = "internalKey";

    private ApiSecurity() {
    }
}
