package dev.keyboard.workstations.block;

import dev.keyboard.workstations.Mc;

//? if >=1.21 {
/* import com.mojang.serialization.MapCodec; */
//?}
import net.minecraft.block.AbstractBlock;
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
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
//? if >=1.21 && <26.1 {
/* import net.minecraft.util.ItemActionResult; */
//?}
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.BlockView;
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
	//? if >=1.21 {
	/* public static final MapCodec<MilkBarrelBlock> CODEC = createCodec(MilkBarrelBlock::new); */
	//?}
	/** How full the barrel looks, in quarters. The count behind it is far finer than the model. */
	public static final IntProperty LEVEL = IntProperty.of("level", 0, 4);

	public MilkBarrelBlock(AbstractBlock.Settings settings) {
		super(settings);
		setDefaultState(getDefaultState().with(LEVEL, 0));
	}

	//? if >=1.21 {
	/* @Override
	protected MapCodec<? extends MilkBarrelBlock> getCodec() {
		return CODEC;
	}
	*/
	//?}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(LEVEL);
	}

	@Override
	public BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.MODEL;
	}

	@Nullable
	@Override
	//? if >=1.17 {
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new MilkBarrelBlockEntity(pos, state);
	}
	//?} else {
	/* public BlockEntity createBlockEntity(BlockView view) {
		return new MilkBarrelBlockEntity();
	} */
	//?}

	/** Brings the shown level back in line with the amount held, if the two have drifted apart. */
	static void showLevel(World world, BlockPos pos, BlockState state, int stored) {
		int shown = stored <= 0 ? 0
				: Math.min(4, (stored + MilkBarrelBlockEntity.CAPACITY / 4 - 1)
						/ (MilkBarrelBlockEntity.CAPACITY / 4));

		if (state.get(LEVEL) != shown) {
			world.setBlockState(pos, state.with(LEVEL, shown), Mc.NOTIFY_ALL);
		}
	}

	/**
	 * Trades a bucket either way: an empty one comes out full, a full one goes in empty. Anything
	 * else in hand, or an empty hand, just reads the level off instead.
	 */
	//? if >=26.1 {
	/* @Override
	protected ActionResult useItemOn(ItemStack stack, BlockState state, World world, BlockPos pos,
			PlayerEntity player, Hand hand, BlockHitResult hit) {
		if (world.isClient) {
			return ActionResult.SUCCESS;
		}

		if (!(world.getBlockEntity(pos) instanceof MilkBarrelBlockEntity barrel)) {
			return ActionResult.TRY_WITH_EMPTY_HAND;
		}

		if (Mc.isOf(stack, Items.BUCKET) && barrel.drain()) {
			swap(player, hand, stack, new ItemStack(Items.MILK_BUCKET));
			announce(world, pos, player, barrel, SoundEvents.ITEM_BUCKET_FILL);
			return ActionResult.SUCCESS;
		}

		if (Mc.isOf(stack, Items.MILK_BUCKET) && barrel.fill()) {
			swap(player, hand, stack, new ItemStack(Items.BUCKET));
			announce(world, pos, player, barrel, SoundEvents.ITEM_BUCKET_EMPTY);
			return ActionResult.SUCCESS;
		}

		return ActionResult.TRY_WITH_EMPTY_HAND;
	}

	@Override
	protected ActionResult useWithoutItem(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (world.isClient) {
			return ActionResult.SUCCESS;
		}

		if (!(world.getBlockEntity(pos) instanceof MilkBarrelBlockEntity barrel)) {
			return ActionResult.PASS;
		}

		Mc.tell(player, level(barrel), true);
		return ActionResult.SUCCESS;
	}
	*/
	//?} elif >=1.21 {
	/* @Override
	protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos,
			PlayerEntity player, Hand hand, BlockHitResult hit) {
		if (world.isClient) {
			return ItemActionResult.SUCCESS;
		}

		if (!(world.getBlockEntity(pos) instanceof MilkBarrelBlockEntity barrel)) {
			return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}

		if (Mc.isOf(stack, Items.BUCKET) && barrel.drain()) {
			swap(player, hand, stack, new ItemStack(Items.MILK_BUCKET));
			announce(world, pos, player, barrel, SoundEvents.ITEM_BUCKET_FILL);
			return ItemActionResult.CONSUME;
		}

		if (Mc.isOf(stack, Items.MILK_BUCKET) && barrel.fill()) {
			swap(player, hand, stack, new ItemStack(Items.BUCKET));
			announce(world, pos, player, barrel, SoundEvents.ITEM_BUCKET_EMPTY);
			return ItemActionResult.CONSUME;
		}

		return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (world.isClient) {
			return ActionResult.SUCCESS;
		}

		if (!(world.getBlockEntity(pos) instanceof MilkBarrelBlockEntity barrel)) {
			return ActionResult.PASS;
		}

		Mc.tell(player, level(barrel), true);
		return ActionResult.CONSUME;
	}
	*/
	//?} else {
	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		if (world.isClient) {
			return ActionResult.SUCCESS;
		}

		if (!(world.getBlockEntity(pos) instanceof MilkBarrelBlockEntity barrel)) {
			return ActionResult.PASS;
		}

		ItemStack held = player.getStackInHand(hand);

		if (Mc.isOf(held, Items.BUCKET) && barrel.drain()) {
			swap(player, hand, held, new ItemStack(Items.MILK_BUCKET));
			announce(world, pos, player, barrel, SoundEvents.ITEM_BUCKET_FILL);
			return ActionResult.CONSUME;
		}

		if (Mc.isOf(held, Items.MILK_BUCKET) && barrel.fill()) {
			swap(player, hand, held, new ItemStack(Items.BUCKET));
			announce(world, pos, player, barrel, SoundEvents.ITEM_BUCKET_EMPTY);
			return ActionResult.CONSUME;
		}

		// Nothing changed hands, but saying how full it is still answers what the player asked.
		Mc.tell(player, level(barrel), true);
		return ActionResult.CONSUME;
	}
	//?}

	/**
	 * Hands back what the traded bucket became.
	 *
	 * <p>A creative player keeps what they were holding, the way vanilla leaves a creative bucket
	 * alone, and is given nothing, since the point of the trade was the milk rather than the tin.
	 */
	private static void swap(PlayerEntity player, Hand hand, ItemStack held, ItemStack returned) {
		if (Mc.creative(player)) {
			return;
		}

		held.decrement(1);

		if (held.isEmpty()) {
			player.setStackInHand(hand, returned);
		} else if (!Mc.inventory(player).insertStack(returned)) {
			player.dropItem(returned, false);
		}
	}

	private static void announce(World world, BlockPos pos, PlayerEntity player,
			MilkBarrelBlockEntity barrel, SoundEvent sound) {
		world.playSound(null, pos, sound, SoundCategory.BLOCKS, 1.0F, 1.0F);
		Mc.tell(player, level(barrel), true);
	}

	private static Text level(MilkBarrelBlockEntity barrel) {
		return Mc.translatable("message.keyboard_workstations.milk_barrel_level",
				barrel.getStored(), MilkBarrelBlockEntity.CAPACITY);
	}

	@Override
	public boolean hasComparatorOutput(BlockState state) {
		return true;
	}

	/** Reads out how full it is, so a hopper line can be told to stop feeding a barrel nobody empties. */
	@Override
	//? if >=26.1 {
	/* protected int getAnalogOutputSignal(BlockState state, World world, BlockPos pos, net.minecraft.util.math.Direction direction) { */
	//?} else {
	public int getComparatorOutput(BlockState state, World world, BlockPos pos) {
	//?}
		if (!(world.getBlockEntity(pos) instanceof MilkBarrelBlockEntity barrel) || barrel.isEmpty()) {
			return 0;
		}

		// Any milk at all is worth one, so an almost empty barrel still reads apart from a bare one.
		return MathHelper.clamp(barrel.getStored() * 15 / MilkBarrelBlockEntity.CAPACITY, 1, 15);
	}
}
