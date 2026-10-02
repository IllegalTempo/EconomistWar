package com.economistwars.ownership.assetsReferences;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** Register additional asset parsers during initialization, before loading saved ownerships. */
public final class AssetsReferenceRegistry {
    private static final Map<String, Function<String, ? extends AssetsReference>> PARSERS =
            new HashMap<>();

    static {
        register("item", ItemAssetsReference::parsePayload);
        register("land", LandAssetsReference::parsePayload);
    }

    private AssetsReferenceRegistry() {}

    public static void register(String type, Function<String, ? extends AssetsReference> parser) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(parser, "parser");
        if (type.isBlank() || type.contains("|")) {
            throw new IllegalArgumentException("Asset type must be nonblank and contain no pipe separators");
        }
        if (PARSERS.putIfAbsent(type, parser) != null) {
            throw new IllegalArgumentException("Duplicate asset type: " + type);
        }
    }

    public static AssetsReference deserialize(String type, String payload) {
        Function<String, ? extends AssetsReference> parser = PARSERS.get(type);
        return parser == null ? null : parser.apply(payload);
    }

    public static AssetsReference deserialize(String type, String payload, com.mojang.serialization.DynamicOps<?> ops) {
        return type.equals("item") ? ItemAssetsReference.parsePayload(payload, ops) : deserialize(type, payload);
    }
}
