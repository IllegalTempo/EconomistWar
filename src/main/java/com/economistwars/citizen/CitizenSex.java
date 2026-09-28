package com.economistwars.citizen;

public enum CitizenSex {
    FEMALE,
    MALE;

    public static CitizenSex fromSavedValue(String value) {
        try {
            return valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            return FEMALE;
        }
    }
}
