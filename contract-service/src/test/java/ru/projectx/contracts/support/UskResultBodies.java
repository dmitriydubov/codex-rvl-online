package ru.projectx.contracts.support;

import java.nio.charset.StandardCharsets;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

public final class UskResultBodies {

    public static final String MESSAGE_ID = "0c6a9f31-58b2-4e77-91d0-3a7e5b1c22f9";
    public static final String CONTRACT_ID = "0f4b8e2a-6c13-4d7e-b9a1-2e5c7d3f8a64";
    public static final String CONTRACT_USK_ID = "a71f3c98-4e20-4a55-93b1-5d0c7e2f8814";
    public static final String POLICY_EXTERNAL_ID = "9e4d2c1b-7a35-4f68-b2c0-5d1e8f3a6b74";
    public static final String CONTRACT_URL = "https://1c-usk.example/rvl/km/DKM-2026-000001.pdf";
    public static final String POLICY_URL = "https://1c-usk.example/rvl/km/0987654321.pdf";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final ObjectNode root;

    private UskResultBodies(ObjectNode root) {
        this.root = root;
    }

    public static UskResultBodies registered() {
        ObjectNode root = JSON.createObjectNode();
        meta(root);
        ObjectNode data = root.putObject("data")
                .put("id", CONTRACT_ID)
                .put("contract_usk_id", CONTRACT_USK_ID)
                .put("contract_usk_number", "DKM-000004417")
                .put("status", "REGISTERED");
        data.putObject("policy")
                .put("external_id", POLICY_EXTERNAL_ID)
                .put("number", "0987654321")
                .put("date_from", "2026-04-10")
                .put("date_to", "2026-04-24");
        data.putArray("documents")
                .add(JSON.createObjectNode().put("doc_type", "CONTRACT").put("file_url", CONTRACT_URL)
                        .put("file_size", 205312))
                .add(JSON.createObjectNode().put("doc_type", "POLICY").put("file_url", POLICY_URL)
                        .put("file_size", 118784));
        return new UskResultBodies(root);
    }

    public static UskResultBodies rejected() {
        ObjectNode root = JSON.createObjectNode();
        meta(root);
        root.putObject("data")
                .put("id", CONTRACT_ID)
                .put("status", "ERROR")
                .put("error_code", "CONTRACT_REJECTED")
                .put("error_message", "Стенд: регистрация договора невозможна");
        return new UskResultBodies(root);
    }

    private static void meta(ObjectNode root) {
        root.putObject("meta")
                .put("message_id", MESSAGE_ID)
                .put("correlation_id", LmPolicyResultBodies.REQUEST_ID)
                .put("topic", "sogaz1c.km-contract.registered")
                .put("event_type", "KmContractRegistered")
                .put("schema_version", "1.0")
                .put("created_at", "2026-02-11T09:15:00+03:00")
                .put("entity_version", 1)
                .put("source", "1C-USK");
    }

    public UskResultBodies withDataField(String name, String value) {
        data().put(name, value);
        return this;
    }

    public UskResultBodies withoutDataField(String name) {
        data().remove(name);
        return this;
    }

    public UskResultBodies withDocumentField(int index, String name, String value) {
        ((ObjectNode) data().get("documents").get(index)).put(name, value);
        return this;
    }

    public UskResultBodies withEmptyDocuments() {
        data().putArray("documents");
        return this;
    }

    private ObjectNode data() {
        return (ObjectNode) root.get("data");
    }

    public String toJson() {
        return JSON.writeValueAsString(root);
    }

    public byte[] toBytes() {
        return toJson().getBytes(StandardCharsets.UTF_8);
    }
}
