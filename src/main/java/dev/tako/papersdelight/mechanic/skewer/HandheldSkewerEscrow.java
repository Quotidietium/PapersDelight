package dev.tako.papersdelight.mechanic.skewer;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

record HandheldSkewerEscrow(UUID sessionId, byte[] sourceBytes, HandheldSkewerStackState state) {

    HandheldSkewerEscrow {
        sourceBytes = sourceBytes.clone();
    }

    static HandheldSkewerEscrow decode(UUID sessionId, byte[] sourceBytes,
                                       Integer remainingRaw, Integer originalAmount) {
        if (sessionId == null || sourceBytes == null || sourceBytes.length == 0) return null;
        if (remainingRaw == null || originalAmount == null) return null;
        if (remainingRaw < 0 || originalAmount < 1 || originalAmount < remainingRaw) return null;
        return new HandheldSkewerEscrow(sessionId, sourceBytes,
                new HandheldSkewerStackState(originalAmount, remainingRaw));
    }

    HandheldSkewerEscrow consumeOne() {
        return new HandheldSkewerEscrow(sessionId, sourceBytes, state.consumeOne());
    }

    void writeTo(ItemMeta meta, NamespacedKey sessionKey, NamespacedKey sourceKey,
                 NamespacedKey originalKey, NamespacedKey rawKey) {
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(sessionKey, PersistentDataType.STRING, sessionId.toString());
        data.set(sourceKey, PersistentDataType.BYTE_ARRAY, sourceBytes);
        data.set(originalKey, PersistentDataType.INTEGER, state.originalAmount());
        data.set(rawKey, PersistentDataType.INTEGER, state.remainingRawAmount());
    }

    @Override
    public byte[] sourceBytes() {
        return sourceBytes.clone();
    }
}
