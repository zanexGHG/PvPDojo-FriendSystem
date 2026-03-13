package dev.zanex.core.labels;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Map;

public final class LabelsJson {
    private final JsonObject labels = new JsonObject();

    public LabelsJson(JsonObject labelsObj) {
        if (labelsObj != null) {
            // Copy into our internal object (avoid JsonObject#keySet() for older Gson compatibility)
            for (Map.Entry<String, JsonElement> entry : labelsObj.entrySet()) {
                this.labels.add(entry.getKey(), entry.getValue());
            }
        }
    }

    public LabelsJson(String labels) {
        this(dev.zanex.core.labels.JsonUtils.parseCompat(labels).getAsJsonObject());
    }

    /**
     * @return root labels JsonObject
     */
    public JsonObject labels() {
        return labels;
    }

    /**
     * Backwards/forwards-compatible accessor (some parts of the plugin might call getLabels()).
     */
    public JsonObject getLabels() {
        return labels;
    }

    public String of(String label) {
        if (label == null) return null;

        String[] parts = label.split("\\.");
        JsonObject current = labels;

        for (int i = 0; i < parts.length - 1; i++) {
            if (!current.has(parts[i])) return null;
            current = current.getAsJsonObject(parts[i]);
        }

        String last = parts[parts.length - 1];
        return current.has(last) ? current.get(last).getAsString() : null;
    }
}
