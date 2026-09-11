package dev.keyboard.workstations.block;

import dev.keyboard.workstations.WorkstationsMod;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * An open trough of feed for the ranch station to draw from, so the feed does not have to live
 * on the same shelves the wool and meat come back onto.
 *
 * <p>Shaped like the milk barrel — a wooden vat you look down into — but lower, with a rim and
 * posts, and hay showing how full it is. How full it looks is a block state, the same trick the
 * milk barrel uses, so the client stays in step without a packet of its own.
 *
 * <p>Unlike the milk barrel it is a real inventory: right-click opens it, hoppers fill it, and
 * the rancher takes from it the way the farmer takes seed from a seed box.
 */
public class FeedBarrelBlock extends BlockWithEntity {
	/** How full the trough looks, in quarters. The count behind it is the inventory itself. */
	public static final IntProperty LEVEL = IntProperty.of("level", 0, 4);

	/** The vat plus the posts that stick up at the corners. */
	private static final VoxelShape SHAPE = Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 10.0, 16.0);

	public FeedBarrelBlock(Settings settings) {
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
	public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return SHAPE;
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new FeedBarrelBlockEntity(pos, state);
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand,
			BlockHitResult hit) {
		if (world.isClient) {
			return ActionResult.SUCCESS;
		}

		NamedScreenHandlerFactory factory = state.createScreenHandlerFactory(world, pos);

		if (factory != null) {
			player.openHandledScreen(factory);
		}

		return ActionResult.CONSUME;
	}

	/**
	 * Brings the shown level back in line with what is actually in the trough. Called from the
	 * block entity whenever the inventory changes, so a hopper filling it and a rancher emptying
	 * it both update the hay without either knowing about models.
	 */
	static void showLevel(World world, BlockPos pos, BlockState state, int shown) {
		if (state.get(LEVEL) != shown) {
			world.setBlockState(pos, state.with(LEVEL, shown), Block.NOTIFY_ALL);
		}
	}

	@Override
	public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.isOf(newState.getBlock()) && world.getBlockEntity(pos) instanceof FeedBarrelBlockEntity trough) {
			ItemScatterer.spawn(world, pos, trough);
			world.updateComparators(pos, this);
		}

		super.onStateReplaced(state, world, pos, newState, moved);
	}

	@Override
	public boolean hasComparatorOutput(BlockState state) {
		return true;
	}

	@Override
	public int getComparatorOutput(BlockState state, World world, BlockPos pos) {
		return ScreenHandler.calculateComparatorOutput(world.getBlockEntity(pos));
	}
}
