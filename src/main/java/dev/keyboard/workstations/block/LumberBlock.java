package dev.keyboard.workstations.block;

import dev.keyboard.workstations.Mc;

import dev.keyboard.workstations.WorkstationsMod;
//? if >=1.21 {
/* import com.mojang.serialization.MapCodec; */
//?}
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

/**
 * The lumber station. Same idea as the farm station and the same handling: normal use opens the
 * container, sneak and use opens the settings screen, and breaking it takes the lumberjack with it.
 */
public class LumberBlock extends BlockWithEntity {
	//? if >=1.21 {
	/* public static final MapCodec<LumberBlock> CODEC = createCodec(LumberBlock::new); */
	//?}
	public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
	/**
	 * Legs at the corners carrying a bench, which is the whole of the block. The axe above it is
	 * left out on purpose: it hangs and turns the way a dropped item does, and there is no
	 * catching hold of one of those either.
	 */
	private static final VoxelShape SHAPE = VoxelShapes.union(
			Block.createCuboidShape(0.0, 0.0, 0.0, 2.0, 10.0, 2.0),
			Block.createCuboidShape(14.0, 0.0, 0.0, 16.0, 10.0, 2.0),
			Block.createCuboidShape(0.0, 0.0, 14.0, 2.0, 10.0, 16.0),
			Block.createCuboidShape(14.0, 0.0, 14.0, 16.0, 10.0, 16.0),
			Block.createCuboidShape(0.0, 10.0, 0.0, 16.0, 12.0, 16.0));

	public LumberBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
	}

	//? if >=1.21 {
	/* @Override
	protected MapCodec<? extends LumberBlock> getCodec() {
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

	/** Kept out of planned paths for the reason {@link RanchBlock} spells out: legs are not a cube. */
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
		return new LumberBlockEntity(pos, state);
	}
	//?} else {
	/* public BlockEntity createBlockEntity(BlockView view) {
		return new LumberBlockEntity();
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
		/* return validateTicker(type, WorkstationsMod.LUMBER_BLOCK_ENTITY, WorkStationBlockEntity::serverTick); */
		//?} else {
		return checkType(type, WorkstationsMod.LUMBER_BLOCK_ENTITY, WorkStationBlockEntity::serverTick);
		//?}
	}
	//?}

	@Override
	public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
		super.onPlaced(world, pos, state, placer, itemStack);

		if (!(world instanceof ServerWorld serverWorld)
				|| !(world.getBlockEntity(pos) instanceof LumberBlockEntity station)) {
			return;
		}

		// Both done here rather than left to the tick timer, so placing the block visibly does
		// something and the player is told straight away how much wood it found.
		int trees = station.surveyWoods(serverWorld).trees().size();
		station.summonWorker(serverWorld);

		if (placer instanceof PlayerEntity player) {
			player.sendMessage(Mc.translatable("message.keyboard_workstations.lumber_placed", trees), true);
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

		if (!(world.getBlockEntity(pos) instanceof LumberBlockEntity)) {
			return ActionResult.PASS;
		}

		NamedScreenHandlerFactory factory = state.createScreenHandlerFactory(world, pos);

		if (factory != null) {
			player.openHandledScreen(factory);
		}

		return ActionResult.CONSUME;
	}

	@Override
	public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.isOf(newState.getBlock()) && world.getBlockEntity(pos) instanceof LumberBlockEntity station) {
			if (world instanceof ServerWorld serverWorld) {
				station.dismissWorker(serverWorld);
			}

			ItemScatterer.spawn(world, pos, station);
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
