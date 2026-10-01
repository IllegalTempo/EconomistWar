package com.economistwars.ownership.assetsReferences;

public abstract class AssetsReference{
    public abstract String getType();

    /** Returns the persisted payload, or null when this reference is incomplete. */
    public abstract String serializePayload();

}
