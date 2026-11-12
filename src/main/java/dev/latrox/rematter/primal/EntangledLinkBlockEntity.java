package dev.latrox.rematter.primal;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public final class EntangledLinkBlockEntity extends BlockEntity {
    private @Nullable UUID owner, pair;
    private @Nullable BlockPos peer;
    public EntangledLinkBlockEntity(BlockPos pos, BlockState state) { super(PrimalContent.ENTANGLED_LINK_ENTITY.get(), pos, state); }
    public @Nullable UUID owner() { return owner; }
    @Nullable UUID pair() { return pair; }
    @Nullable BlockPos target() { return peer; }
    void claim(UUID user) { if (owner == null) { owner = user; endpointChanged(); } }
    void pair(BlockPos target, UUID id) { peer = target.immutable(); pair = id; endpointChanged(); }
    private void endpointChanged() {
        setChanged();
        if (level instanceof net.minecraft.server.level.ServerLevel server) dev.latrox.rematter.content.ChunkRevisions.touchLoaded(server, worldPosition);
    }
    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output); output.putInt("schema", 1);
        if (owner != null) output.putString("owner", owner.toString());
        if (pair != null && peer != null) { output.putString("pair", pair.toString()); output.putLong("peer", peer.asLong()); }
    }
    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input); owner = null; pair = null; peer = null;
        if (input.getIntOr("schema", -1) != 1) return;
        try {
            var savedOwner = input.getString("owner"); if (savedOwner.isPresent()) owner = UUID.fromString(savedOwner.get());
            var savedPair = input.getString("pair");
            if (savedPair.isPresent()) { pair = UUID.fromString(savedPair.get()); peer = BlockPos.of(input.getLongOr("peer", 0)); }
        } catch (IllegalArgumentException invalid) { pair = null; peer = null; }
    }
}
