package dev.keyboard.workstations.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * An open vat the rancher empties its milking into, so that milk stops competing for the station's
 * shelves. Whoever wants it brings a bucket to the barrel.
 *
 * <p>How full it looks is a block state rather than anything drawn by hand, which is what keeps the
 * client in step without a packet: block states are already kept in step, and the exact count only
 * ever matters on the server.
 */
public class MilkBarrelBlock extends BlockWithEntity {
	/** How full the barrel looks, in quarters. The count behind it is far finer than the model. */
	public static final IntProperty LEVEL = IntProperty.of("level", 0, 4);

	public MilkBarrelBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(LEVEL, 0));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(LEVEL);
	}

	@Override
	public BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.MODEL;
	}

	@Override
	public BlockEntity createBlockEntity(BlockView view) {
		return new MilkBarrelBlockEntity();
	}

	/** Brings the shown level back in line with the amount held, if the two have drifted apart. */
	static void showLevel(World world, BlockPos pos, BlockState state, int stored) {
		int shown = stored <= 0 ? 0
				: Math.min(4, (stored + MilkBarrelBlockEntity.CAPACITY / 4 - 1)
						/ (MilkBarrelBlockEntity.CAPACITY / 4));

		if (state.get(LEVEL) != shown) {
			world.setBlockState(pos, state.with(LEVEL, shown), 3);
		}
	}

	/**
	 * Trades a bucket either way: an empty one comes out full, a full one goes in empty. Anything
	 * else in hand, or an empty hand, just reads the level off instead.
	 */
	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		if (world.isClient) {
			return ActionResult.SUCCESS;
		}

		if (!(world.getBlockEntity(pos) instanceof MilkBarrelBlockEntity barrel)) {
			return ActionResult.PASS;
		}

		ItemStack held = player.getStackInHand(hand);

		if (held.getItem() == Items.BUCKET && barrel.drain()) {
			swap(player, hand, held, new ItemStack(Items.MILK_BUCKET));
			announce(world, pos, player, barrel, SoundEvents.ITEM_BUCKET_FILL);
			return ActionResult.CONSUME;
		}

		if (held.getItem() == Items.MILK_BUCKET && barrel.fill()) {
			swap(player, hand, held, new ItemStack(Items.BUCKET));
			announce(world, pos, player, barrel, SoundEvents.ITEM_BUCKET_EMPTY);
			return ActionResult.CONSUME;
		}

		// Nothing changed hands, but saying how full it is still answers what the player asked.
		player.sendMessage(level(barrel), true);
		return ActionResult.CONSUME;
	}

	/**
	 * Hands back what the traded bucket became.
	 *
	 * <p>A creative player keeps what they were holding, the way vanilla leaves a creative bucket
	 * alone, and is given nothing, since the point of the trade was the milk rather than the tin.
	 */
	private static void swap(PlayerEntity player, Hand hand, ItemStack held, ItemStack returned) {
		if (player.abilities.creativeMode) {
			return;
		}

		held.decrement(1);

		if (held.isEmpty()) {
			player.setStackInHand(hand, returned);
		} else if (!player.inventory.insertStack(returned)) {
			player.dropItem(returned, false);
		}
	}

	private static void announce(World world, BlockPos pos, PlayerEntity player,
			MilkBarrelBlockEntity barrel, SoundEvent sound) {
		world.playSound(null, pos, sound, SoundCategory.BLOCKS, 1.0F, 1.0F);
		player.sendMessage(level(barrel), true);
	}

	private static Text level(MilkBarrelBlockEntity barrel) {
		return new TranslatableText("message.keyboard_workstations.milk_barrel_level",
				barrel.getStored(), MilkBarrelBlockEntity.CAPACITY);
	}

	@Override
	public boolean hasComparatorOutput(BlockState state) {
		return true;
	}

	/** Reads out how full it is, so a hopper line can be told to stop feeding a barrel nobody empties. */
	@Override
	public int getComparatorOutput(BlockState state, World world, BlockPos pos) {
		if (!(world.getBlockEntity(pos) instanceof MilkBarrelBlockEntity barrel) || barrel.isEmpty()) {
			return 0;
		}

		// Any milk at all is worth one, so an almost empty barrel still reads apart from a bare one.
		return MathHelper.clamp(barrel.getStored() * 15 / MilkBarrelBlockEntity.CAPACITY, 1, 15);
	}
}
