package dev.zanex.core.labels;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

public final class JsonUtils {
    private JsonUtils() {
    }

    /**
     * @return a JsonElement and just checks if the new way of parsing a Json string works. If it doesn't, then catch the exception and use the old and deprecated way to parse Json strings.
     */
    public static JsonElement parseCompat(String json) {
        try {
            return (JsonElement) JsonParser.class
                    .getMethod("parseString", String.class)
                    .invoke(null, json);
        } catch (Throwable ignored) {
            return new JsonParser().parse(json);
        }
    }
}

