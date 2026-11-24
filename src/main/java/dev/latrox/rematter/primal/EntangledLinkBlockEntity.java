package dev.latrox.rematter.primal;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public final class EntangledLinkBlockEntity extends BlockEntity {
    private @Nullable UUID owner, pair;
    private @Nullable BlockPos peer;
    private dev.latrox.rematter.content.SecurityMode securityMode = dev.latrox.rematter.content.SecurityMode.PUBLIC;
    public EntangledLinkBlockEntity(BlockPos pos, BlockState state) { super(PrimalContent.ENTANGLED_LINK_ENTITY.get(), pos, state); }
    public @Nullable UUID owner() { return owner; }
    public dev.latrox.rematter.content.SecurityMode securityMode() { return securityMode; }
    public boolean canManageSecurity(net.minecraft.world.entity.player.Player player) {
        return dev.latrox.rematter.content.MachineAccess.canManage(owner, player);
    }
    public boolean setSecurityMode(net.minecraft.world.entity.player.Player player, dev.latrox.rematter.content.SecurityMode next) {
        if (isRemoved() || player.isSpectator() || !canManageSecurity(player) || next == null) return false;
        if (securityMode == next) return true;
        securityMode = next;
        endpointChanged();
        if (level instanceof net.minecraft.server.level.ServerLevel server) {
            dev.latrox.rematter.content.MatterCableBlock.refreshLoadedAround(server, worldPosition);
        }
        return true;
    }
    public boolean canAccess(net.minecraft.world.entity.player.Player player) {
        return dev.latrox.rematter.content.MachineAccess.allows(level, owner, securityMode, player);
    }
    public boolean canRemove(net.minecraft.world.entity.player.Player player) {
        return canAccess(player);
    }
    @Nullable UUID pair() { return pair; }
    @Nullable BlockPos target() { return peer; }
    void claim(UUID user) { if (owner == null) { owner = user; endpointChanged(); } }
    void placedBy(@Nullable LivingEntity placer) {
        if (placer instanceof net.minecraft.world.entity.player.Player player
                && !(player instanceof net.neoforged.neoforge.common.util.FakePlayer)) {
            if (securityMode == dev.latrox.rematter.content.SecurityMode.PUBLIC) {
                securityMode = dev.latrox.rematter.content.MachineAccess.placementDefault(level, placer);
            }
            claim(player.getUUID());
            if (level instanceof net.minecraft.server.level.ServerLevel server) {
                dev.latrox.rematter.content.MatterCableBlock.refreshLoadedAround(server, worldPosition);
            }
        }
    }
    void pair(BlockPos target, UUID id) { peer = target.immutable(); pair = id; endpointChanged(); }
    private void endpointChanged() {
        setChanged();
        if (level instanceof net.minecraft.server.level.ServerLevel server) dev.latrox.rematter.content.ChunkRevisions.touchLoaded(server, worldPosition);
    }
    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output); output.putInt("schema", 1);
        if (owner != null) output.putString("owner", owner.toString());
        if (securityMode != dev.latrox.rematter.content.SecurityMode.PUBLIC) output.putString("security", securityMode.getSerializedName());
        if (pair != null && peer != null) { output.putString("pair", pair.toString()); output.putLong("peer", peer.asLong()); }
    }
    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input); owner = null; pair = null; peer = null; securityMode = dev.latrox.rematter.content.SecurityMode.PUBLIC;
        if (input.getIntOr("schema", -1) != 1) return;
        try {
            var savedOwner = input.getString("owner"); if (savedOwner.isPresent()) owner = UUID.fromString(savedOwner.get());
            securityMode = input.getString("security").filter(dev.latrox.rematter.content.SecurityMode::validName).map(dev.latrox.rematter.content.SecurityMode::load).orElse(dev.latrox.rematter.content.SecurityMode.PUBLIC);
            var savedPair = input.getString("pair");
            if (savedPair.isPresent()) { pair = UUID.fromString(savedPair.get()); peer = BlockPos.of(input.getLongOr("peer", 0)); }
        } catch (IllegalArgumentException invalid) { pair = null; peer = null; }
    }
}
