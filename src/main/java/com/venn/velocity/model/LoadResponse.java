package com.venn.velocity.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"id", "customer_id", "accepted"})
public class LoadResponse {

    private final String id;

    @JsonProperty("customer_id")
    private final String customerId;

    private final boolean accepted;

    public LoadResponse(String id, String customerId, boolean accepted) {
        this.id = id;
        this.customerId = customerId;
        this.accepted = accepted;
    }

    public String getId() { return id; }
    public String getCustomerId() { return customerId; }
    public boolean isAccepted() { return accepted; }
}
