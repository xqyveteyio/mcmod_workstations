package dev.keyboard.workstations.block;

import dev.keyboard.workstations.Mc;

import dev.keyboard.workstations.WorkstationsMod;
//? if >=1.21 {
/* import com.mojang.serialization.MapCodec; */
//?}
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
//? if >=1.17 {
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
//?}
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
//? if >=1.17 {
import net.minecraft.util.math.random.Random;
//?} else {
/* import java.util.Random; */
//?}
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * A chest of feed for the ranch station beside it to draw from, so the feed does not have to live
 * on the same shelves the wool and meat come back onto.
 *
 * <p>An ordinary chest to look at and to open, drawn from vanilla's own chest model, but not a
 * {@code ChestBlock} underneath: two of these never join into one double chest. That is not a
 * limitation to work around. A station sweeps its work area and takes on every box it finds, and a
 * container spread across two positions would turn up twice in that sweep, so the joining is the
 * part deliberately left out.
 */
public class FeedBarrelBlock extends BlockWithEntity {
	//? if >=1.21 {
	/* public static final MapCodec<FeedBarrelBlock> CODEC = createCodec(FeedBarrelBlock::new); */
	//?}
	public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;

	/** A chest's own outline: a hair inside the block on every side but the bottom. */
	private static final VoxelShape SHAPE = Block.createCuboidShape(1.0, 0.0, 1.0, 15.0, 14.0, 15.0);

	public FeedBarrelBlock(AbstractBlock.Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
	}

	//? if >=1.21 {
	/* @Override
	protected MapCodec<? extends FeedBarrelBlock> getCodec() {
		return CODEC;
	}
	*/
	//?}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Nullable
	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		return getDefaultState().with(FACING, Mc.horizontalFacing(ctx).getOpposite());
	}

	@Override
	public BlockState rotate(BlockState state, BlockRotation rotation) {
		return state.with(FACING, rotation.rotate(state.get(FACING)));
	}

	@Override
	public BlockState mirror(BlockState state, BlockMirror mirror) {
		return state.rotate(mirror.getRotation(state.get(FACING)));
	}

	@Override
	public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return SHAPE;
	}

	/**
	 * The block itself is drawn by nothing: its whole body is the chest model, which the block
	 * entity's renderer puts up. All the block model behind it carries is the texture to break into
	 * particles.
	 */
	@Override
	public BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.ENTITYBLOCK_ANIMATED;
	}

	@Nullable
	@Override
	//? if >=1.17 {
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new FeedBarrelBlockEntity(pos, state);
	}
	//?} else {
	/* public BlockEntity createBlockEntity(BlockView view) {
		return new FeedBarrelBlockEntity();
	} */
	//?}

	/** Client side only: the lid's angle is the one thing that has to be kept moving every tick. */
	//? if >=1.17 {
	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		if (!world.isClient) {
			return null;
		}

		//? if >=1.21 {
		/* return validateTicker(type, WorkstationsMod.FEED_BARREL_BLOCK_ENTITY, FeedBarrelBlockEntity::clientTick); */
		//?} else {
		return checkType(type, WorkstationsMod.FEED_BARREL_BLOCK_ENTITY, FeedBarrelBlockEntity::clientTick);
		//?}
	}
	//?}

	@Override
	//? if >=1.21 {
	/* protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) { */
	//?} else {
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
	//?}
		if (world.isClient) {
			return ActionResult.SUCCESS;
		}

		NamedScreenHandlerFactory factory = state.createScreenHandlerFactory(world, pos);

		if (factory != null) {
			player.openHandledScreen(factory);
		}

		return ActionResult.CONSUME;
	}

	/** Recounts who has the box open, so a lid left up by a player who logged out comes back down. */
	@Override
	public void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
		if (world.getBlockEntity(pos) instanceof FeedBarrelBlockEntity box) {
			box.recountViewers();
		}
	}

	@Override
	//? if <26.1 {
	public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.isOf(newState.getBlock()) && world.getBlockEntity(pos) instanceof FeedBarrelBlockEntity box) {
			ItemScatterer.spawn(world, pos, box);
			world.updateComparators(pos, this);
		}

		super.onStateReplaced(state, world, pos, newState, moved);
	}

	//?}

	@Override
	public boolean hasComparatorOutput(BlockState state) {
		return true;
	}

	@Override
	//? if >=26.1 {
	/* protected int getAnalogOutputSignal(BlockState state, World world, BlockPos pos, Direction direction) { */
	//?} else {
	public int getComparatorOutput(BlockState state, World world, BlockPos pos) {
	//?}
		return ScreenHandler.calculateComparatorOutput(world.getBlockEntity(pos));
	}
}
