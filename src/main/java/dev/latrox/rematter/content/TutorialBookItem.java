package dev.latrox.rematter.content;

import dev.latrox.rematter.guide.GuideMeIntegration;
import java.util.stream.IntStream;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.WrittenBookItem;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

/** A usable vanilla book, with an optional richer GuideME presentation. */
public final class TutorialBookItem extends WrittenBookItem {
    public static final int PAGE_COUNT = 49;

    public TutorialBookItem(Properties properties) {
        super(properties.stacksTo(1).component(DataComponents.WRITTEN_BOOK_CONTENT,
                new WrittenBookContent(Filterable.passThrough("ReMatter Guide"), "ReMatter", 0,
                        IntStream.rangeClosed(1, PAGE_COUNT)
                                .mapToObj(page -> Filterable.<Component>passThrough(Component.translatable("guide.rematter.page." + page)))
                                .toList(), true)));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        // Sneaking always opens the dependency-free book, including when GuideME is installed.
        if (!player.isShiftKeyDown() && ModList.get().isLoaded("guideme")) {
            if (!level.isClientSide()) {
                GuideMeIntegration.open(player);
                player.awardStat(Stats.ITEM_USED.get(this));
            }
            return InteractionResult.SUCCESS;
        }
        return super.use(level, player, hand);
    }
}
