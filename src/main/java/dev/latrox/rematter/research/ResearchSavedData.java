package dev.latrox.rematter.research;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import dev.latrox.rematter.ReMatter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Overworld-owned save: dimensions and machines never create competing copies of a research identity. */
public final class ResearchSavedData extends SavedData {
    private static final Codec<ResearchSavedData> CODEC = Codec.PASSTHROUGH.xmap(
            raw -> new ResearchSavedData(ResearchLedger.fromJson(raw.convert(JsonOps.INSTANCE).getValue().getAsJsonObject())),
            data -> new Dynamic<>(JsonOps.INSTANCE, data.ledger.toJson()));
    private static final SavedDataType<ResearchSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(ReMatter.MOD_ID, "material_research"), ResearchSavedData::new, CODEC);
    private ResearchLedger ledger;
    public ResearchSavedData() { this(new ResearchLedger()); }
    private ResearchSavedData(ResearchLedger ledger) { this.ledger = ledger; ledger.onChanged(this::setDirty); }
    public ResearchLedger ledger() { return ledger; }
    void replaceLedger(ResearchLedger ledger) { this.ledger = ledger; ledger.onChanged(this::setDirty); setDirty(); }
    public static ResearchSavedData get(MinecraftServer server) {
        var data = server.overworld().getDataStorage().computeIfAbsent(TYPE);
        ResearchAuthority.attach(server, data); return data;
    }
}
