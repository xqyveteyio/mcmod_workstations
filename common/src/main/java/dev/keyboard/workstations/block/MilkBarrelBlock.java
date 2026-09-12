package dev.keyboard.workstations.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * An open vat the rancher empties its milking into, so that milk stops competing for the station's
 * shelves. Whoever wants it brings a bucket to the barrel.
 *
 * <p>How full it looks is a block state rather than anything drawn by hand, which is what keeps the
 * client in step without a packet: block states are already kept in step, and the exact count only
 * ever matters on the server.
 */
public class MilkBarrelBlock extends BaseEntityBlock {
	public static final MapCodec<MilkBarrelBlock> CODEC = simpleCodec(MilkBarrelBlock::new);
	/** How full the barrel looks, in quarters. The count behind it is far finer than the model. */
	public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, 4);

	public MilkBarrelBlock(Properties settings) {
		super(settings);
		registerDefaultState(getStateDefinition().any().setValue(LEVEL, 0));
	}

	@Override
	protected MapCodec<? extends MilkBarrelBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(LEVEL);
	}

	@Override
	public RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new MilkBarrelBlockEntity(pos, state);
	}

	/** Brings the shown level back in line with the amount held, if the two have drifted apart. */
	static void showLevel(Level world, BlockPos pos, BlockState state, int stored) {
		int shown = stored <= 0 ? 0
				: Math.min(4, (stored + MilkBarrelBlockEntity.CAPACITY / 4 - 1)
						/ (MilkBarrelBlockEntity.CAPACITY / 4));

		if (state.getValue(LEVEL) != shown) {
			world.setBlock(pos, state.setValue(LEVEL, shown), Block.UPDATE_ALL);
		}
	}

	/**
	 * Trades a bucket either way: an empty one comes out full, a full one goes in empty. Anything
	 * else in hand, or an empty hand, just reads the level off instead.
	 */
	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level world, BlockPos pos,
			Player player, InteractionHand hand, BlockHitResult hit) {
		if (world.isClientSide()) {
			return InteractionResult.SUCCESS;
		}

		if (!(world.getBlockEntity(pos) instanceof MilkBarrelBlockEntity barrel)) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}

		if (stack.is(Items.BUCKET) && barrel.drain()) {
			swap(player, hand, stack, new ItemStack(Items.MILK_BUCKET));
			announce(world, pos, player, barrel, SoundEvents.BUCKET_FILL);
			return InteractionResult.CONSUME;
		}

		if (stack.is(Items.MILK_BUCKET) && barrel.fill()) {
			swap(player, hand, stack, new ItemStack(Items.BUCKET));
			announce(world, pos, player, barrel, SoundEvents.BUCKET_EMPTY);
			return InteractionResult.CONSUME;
		}

		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
		if (world.isClientSide()) {
			return InteractionResult.SUCCESS;
		}

		if (!(world.getBlockEntity(pos) instanceof MilkBarrelBlockEntity barrel)) {
			return InteractionResult.PASS;
		}

		// Nothing changed hands, but saying how full it is still answers what the player asked.
		player.sendOverlayMessage(level(barrel));
		return InteractionResult.CONSUME;
	}

	/**
	 * Hands back what the traded bucket became.
	 *
	 * <p>A creative player keeps what they were holding, the way vanilla leaves a creative bucket
	 * alone, and is given nothing, since the point of the trade was the milk rather than the tin.
	 */
	private static void swap(Player player, InteractionHand hand, ItemStack held, ItemStack returned) {
		if (player.getAbilities().instabuild) {
			return;
		}

		held.shrink(1);

		if (held.isEmpty()) {
			player.setItemInHand(hand, returned);
		} else if (!player.getInventory().add(returned)) {
			player.drop(returned, false);
		}
	}

	private static void announce(Level world, BlockPos pos, Player player,
			MilkBarrelBlockEntity barrel, SoundEvent sound) {
		world.playSound(null, pos, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
		player.sendOverlayMessage(level(barrel));
	}

	private static Component level(MilkBarrelBlockEntity barrel) {
		return Component.translatable("message.villager_workstations.milk_barrel_level",
				barrel.getStored(), MilkBarrelBlockEntity.CAPACITY);
	}

	@Override
	public boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	/** Reads out how full it is, so a hopper line can be told to stop feeding a barrel nobody empties. */
	@Override
	public int getAnalogOutputSignal(BlockState state, Level world, BlockPos pos, Direction direction) {
		if (!(world.getBlockEntity(pos) instanceof MilkBarrelBlockEntity barrel) || barrel.isEmpty()) {
			return 0;
		}

		// Any milk at all is worth one, so an almost empty barrel still reads apart from a bare one.
		return Mth.clamp(barrel.getStored() * 15 / MilkBarrelBlockEntity.CAPACITY, 1, 15);
	}
}
