package dev.quarkos.cosmetics.gallery;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;

public final class Jsons {
    private final ObjectMapper mapper;

    public Jsons(int maxStringLength) {
        this.mapper = JsonMapper.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        mapper.getFactory().setStreamReadConstraints(StreamReadConstraints.builder()
                .maxStringLength(Math.max(1024, maxStringLength))
                .maxNumberLength(32)
                .maxNestingDepth(20)
                .build());
    }

    public ObjectMapper mapper() {
        return mapper;
    }

    public byte[] write(Object value) {
        try {
            return mapper.writeValueAsBytes(value);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not write JSON.", ex);
        }
    }

    public ObjectNode object() {
        return mapper.createObjectNode();
    }

    /**
     * Parses one JSON value. Empty input returns null. Trailing tokens are rejected.
     */
    public com.fasterxml.jackson.databind.JsonNode parse(byte[] body) {
        if (body == null || body.length == 0) {
            return null;
        }
        try (JsonParser parser = mapper.createParser(body)) {
            com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(parser);
            if (node == null) {
                throw new ApiException(400, "invalid_json", "The body is not JSON.");
            }
            if (parser.nextToken() != null) {
                throw new ApiException(400, "invalid_json", "The body has trailing data after the JSON value.");
            }
            return node;
        } catch (ApiException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new ApiException(400, "invalid_json", "The body is not JSON.");
        }
    }
}
