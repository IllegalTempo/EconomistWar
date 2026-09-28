package com.economistwars.citizen;

import java.util.UUID;
import net.minecraft.resources.Identifier;

public record CitizenIdentity(UUID id, String name, CitizenSex sex, Identifier skin) {
    private static final String[] FEMALE_NAMES = {"Ada", "Amara", "Anika", "Elena", "Freya", "Iris", "Mara", "Nia", "Talia", "Zara"};
    private static final String[] MALE_NAMES = {"Ari", "Darius", "Elias", "Felix", "Idris", "Jonas", "Luca", "Milo", "Ravi", "Theo"};

    public static CitizenIdentity create() {
        UUID id = UUID.randomUUID();
        long seed = id.getMostSignificantBits() ^ id.getLeastSignificantBits();
        java.util.Random random = new java.util.Random(seed);
        CitizenSex sex = random.nextBoolean() ? CitizenSex.FEMALE : CitizenSex.MALE;
        return create(id, sex, random);
    }

    public static CitizenIdentity create(CitizenSex sex) {
        UUID id = UUID.randomUUID();
        long seed = id.getMostSignificantBits() ^ id.getLeastSignificantBits();
        return create(id, sex, new java.util.Random(seed));
    }

    private static CitizenIdentity create(UUID id, CitizenSex sex, java.util.Random random) {
        String[] names = sex == CitizenSex.FEMALE ? FEMALE_NAMES : MALE_NAMES;
        String name = names[random.nextInt(names.length)];
        int skinVariant = random.nextInt(2) + 1;
        String skinName = (sex == CitizenSex.FEMALE ? "female" : "male") + "_" + skinVariant;
        return new CitizenIdentity(id, name, sex, Identifier.fromNamespaceAndPath("economistwars", "textures/entity/citizen/" + skinName + ".png"));
    }
}
