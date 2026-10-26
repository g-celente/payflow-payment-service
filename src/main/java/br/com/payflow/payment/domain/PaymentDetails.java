package br.com.payflow.payment.domain;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

// Free fields the merchant controls: a description and up to 20 metadata entries (e.g. the store's order id).
public record PaymentDetails(String description, Map<String, String> metadata) {

    public static final int MAX_DESCRIPTION_LENGTH = 255;
    public static final int MAX_METADATA_ENTRIES = 20;
    public static final int MAX_METADATA_VALUE_LENGTH = 500;
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9_.-]{1,40}");

    public static final PaymentDetails EMPTY = new PaymentDetails(null, Map.of());

    public PaymentDetails {
        if (description != null && description.isBlank()) {
            description = null;
        }
        if (description != null && description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new DomainValidationException("description must have at most " + MAX_DESCRIPTION_LENGTH + " characters");
        }
        metadata = metadata == null ? Map.of() : metadata;
        if (metadata.size() > MAX_METADATA_ENTRIES) {
            throw new DomainValidationException("metadata accepts at most " + MAX_METADATA_ENTRIES + " entries");
        }
        metadata.forEach((key, value) -> {
            if (key == null || !KEY.matcher(key).matches()) {
                throw new DomainValidationException("metadata keys use 1 to 40 letters, digits, _ . or -");
            }
            if (value == null || value.length() > MAX_METADATA_VALUE_LENGTH) {
                throw new DomainValidationException("metadata values must have at most " + MAX_METADATA_VALUE_LENGTH + " characters");
            }
        });
        metadata = Collections.unmodifiableMap(new TreeMap<>(metadata));
    }

    // PATCH semantics: a null argument keeps the current value.
    public PaymentDetails merge(String newDescription, Map<String, String> newMetadata) {
        return new PaymentDetails(newDescription == null ? description : newDescription,
                newMetadata == null ? metadata : newMetadata);
    }
}
