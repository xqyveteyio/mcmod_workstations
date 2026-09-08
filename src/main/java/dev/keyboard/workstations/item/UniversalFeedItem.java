package dev.keyboard.workstations.item;

import java.util.List;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Feed a rancher will offer to anything, standing in for whatever the animal in front of it would
 * normally want.
 *
 * <p>Nothing but an item with a line of explanation on it. What makes it feed is the rancher
 * accepting it alongside each animal's own taste, and there is nowhere else in the game it does
 * anything, so the tooltip has to say where it is meant to go.
 */
public class UniversalFeedItem extends Item {
	public UniversalFeedItem(Settings settings) {
		super(settings);
	}

	@Override
	public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
		tooltip.add(Text.translatable("item.workstations.universal_feed.tooltip").formatted(Formatting.GRAY));
	}
}
