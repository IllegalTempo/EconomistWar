package com.economistwars.household;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class Household {
    private final UUID id;
    private final LinkedHashSet<UUID> members;

    Household(UUID id) {
        this(id, new LinkedHashSet<>());
    }

    Household(UUID id, Set<UUID> members) {
        this.id = id;
        this.members = new LinkedHashSet<>(members);
    }

    public UUID id() {
        return id;
    }

    public Set<UUID> members() {
        return Set.copyOf(members);
    }

    boolean add(UUID citizenId) {
        return members.add(citizenId);
    }

    boolean remove(UUID citizenId) {
        return members.remove(citizenId);
    }

    boolean isEmpty() {
        return members.isEmpty();
    }
}
