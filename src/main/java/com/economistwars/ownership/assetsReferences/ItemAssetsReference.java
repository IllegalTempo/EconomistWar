package com.economistwars.ownership.assetsReferences;

import java.util.UUID;

public class ItemAssetsReference extends AssetsReference{
    private final UUID itemUUID;


    public ItemAssetsReference(UUID itemUUID) {
        this.itemUUID = itemUUID;
    }

    public UUID getItemUUID() {
        return itemUUID;
    }

    @Override
    public String getType() {
        return "item";
    }

    @Override
    public String serializePayload() {
        return itemUUID == null ? null : itemUUID.toString();
    }

}
