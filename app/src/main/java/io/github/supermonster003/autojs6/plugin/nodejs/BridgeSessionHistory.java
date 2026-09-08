package io.github.supermonster003.autojs6.plugin.nodejs;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/** The small diagnostic tail of a session, independent of delivered response data. */
final class BridgeSessionHistory {
    static final int MAX_RESPONSES = 32;
    static final int MAX_RESPONSE_BYTES = 1536;

    private final Set<String> requests = new HashSet<>();
    private final ArrayDeque<String> responses = new ArrayDeque<>();

    synchronized boolean beginRequest(String name) {
        return requests.add(name);
    }

    synchronized void forgetRequest(String name) {
        requests.remove(name);
    }

    synchronized int requestCount() {
        return requests.size();
    }

    synchronized int responseCount() {
        return responses.size();
    }

    synchronized void recordResponse(String json) {
        // A count limit alone still retains megabytes for image/network responses.
        // Keep their wire data intact and replace only the diagnostic copy.
        if (json.length() > MAX_RESPONSE_BYTES || json.getBytes(StandardCharsets.UTF_8).length > MAX_RESPONSE_BYTES) {
            json = "{\"diagnosticTruncated\":true,\"responseCharacters\":" + json.length() + "}";
        }
        if (responses.size() == MAX_RESPONSES) {
            responses.removeFirst();
        }
        responses.addLast(json);
    }

    synchronized String responsesJson() {
        StringBuilder json = new StringBuilder("[");
        for (String response : responses) {
            if (json.length() > 1) json.append(',');
            json.append(response);
        }
        return json.append(']').toString();
    }
}
