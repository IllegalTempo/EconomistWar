package com.economistwars.citizen;

/** Skills that can improve through citizen work. */
public enum CitizenSkill {
    FARMING("farming"),
    MINING("mining"),
    BUILDING("building"),
    BAKERY("bakery");

    private final String serializedName;

    CitizenSkill(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }
}
