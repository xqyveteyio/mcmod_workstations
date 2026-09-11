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
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.pathing.NavigationType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class RanchBlock extends BlockWithEntity {
	//? if >=1.21 {
	/* public static final MapCodec<RanchBlock> CODEC = createCodec(RanchBlock::new); */
	//?}
	public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
	/**
	 * Matches the table model: legs at the corners, the top they carry, and the miniature fence
	 * ringing it. The fence is four walls rather than one lid, so the pen holding the display
	 * animals stays as open as it looks.
	 */
	private static final VoxelShape SHAPE = VoxelShapes.union(
			Block.createCuboidShape(0.0, 0.0, 0.0, 2.0, 10.0, 2.0),
			Block.createCuboidShape(14.0, 0.0, 0.0, 16.0, 10.0, 2.0),
			Block.createCuboidShape(0.0, 0.0, 14.0, 2.0, 10.0, 16.0),
			Block.createCuboidShape(14.0, 0.0, 14.0, 16.0, 10.0, 16.0),
			Block.createCuboidShape(0.0, 10.0, 0.0, 16.0, 12.0, 16.0),
			Block.createCuboidShape(0.0, 12.0, 0.0, 16.0, 16.0, 1.0),
			Block.createCuboidShape(0.0, 12.0, 15.0, 16.0, 16.0, 16.0),
			Block.createCuboidShape(0.0, 12.0, 1.0, 1.0, 16.0, 15.0),
			Block.createCuboidShape(15.0, 12.0, 1.0, 16.0, 16.0, 15.0));

	public RanchBlock(AbstractBlock.Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
	}

	//? if >=1.21 {
	/* @Override
	protected MapCodec<? extends RanchBlock> getCodec() {
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
	 * Kept out of every path the game plans. Vanilla decides a block may be walked through from
	 * whether its collision box fills the cube, and a table standing on legs does not fill it, so
	 * without this the station reads as open ground: the rancher coming home to unload is routed
	 * straight through its own station and then stands wedged against the tabletop, which is solid
	 * and is the one part of the shape the path never accounted for.
	 */
	@Override
	//? if >=1.21 {
	/* protected boolean canPathfindThrough(BlockState state, NavigationType type) { */
	//?} else {
	public boolean canPathfindThrough(BlockState state, BlockView world, BlockPos pos, NavigationType type) {
	//?}
		return false;
	}

	@Override
	public BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.MODEL;
	}

	@Nullable
	@Override
	//? if >=1.17 {
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new RanchBlockEntity(pos, state);
	}
	//?} else {
	/* public BlockEntity createBlockEntity(BlockView view) {
		return new RanchBlockEntity();
	} */
	//?}

	//? if >=1.17 {
	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		if (world.isClient) {
			return null;
		}

		//? if >=1.21 {
		/* return validateTicker(type, WorkstationsMod.RANCH_BLOCK_ENTITY, WorkStationBlockEntity::serverTick); */
		//?} else {
		return checkType(type, WorkstationsMod.RANCH_BLOCK_ENTITY, WorkStationBlockEntity::serverTick);
		//?}
	}
	//?}

	@Override
	public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
		super.onPlaced(world, pos, state, placer, itemStack);

		if (!(world instanceof ServerWorld serverWorld)
				|| !(world.getBlockEntity(pos) instanceof RanchBlockEntity station)) {
			return;
		}

		// Summoned here rather than waiting on the tick timer, so placing the block visibly does something.
		station.summonWorker(serverWorld);

		if (placer instanceof PlayerEntity player) {
			player.sendMessage(Mc.translatable("message.keyboard_workstations.station_placed",
					station.getSettings().workAlong, station.getSettings().workAcross,
					station.getSettings().workAbove, station.getSettings().workBelow), true);
		}
	}

	@Override
	//? if >=1.21 {
	/* protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) { */
	//?} else {
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
	//?}
		if (world.isClient) {
			return ActionResult.SUCCESS;
		}

		if (!(world.getBlockEntity(pos) instanceof RanchBlockEntity station)) {
			return ActionResult.PASS;
		}

		NamedScreenHandlerFactory factory = state.createScreenHandlerFactory(world, pos);

		if (factory != null) {
			player.openHandledScreen(factory);
		}

		return ActionResult.CONSUME;
	}

	@Override
	//? if <26.1 {
	public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.isOf(newState.getBlock()) && world.getBlockEntity(pos) instanceof RanchBlockEntity station) {
			if (world instanceof ServerWorld serverWorld) {
				station.dismissWorker(serverWorld);
			}

			ItemScatterer.spawn(world, pos, station);
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
