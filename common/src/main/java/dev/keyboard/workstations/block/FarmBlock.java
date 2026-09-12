package dev.keyboard.workstations.block;

import com.mojang.serialization.MapCodec;
import dev.keyboard.workstations.WorkstationsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The farm station. Same idea as the ranch station and the same handling: normal use opens the
 * container, sneak and use opens the settings screen, and breaking it takes the farmer with it.
 */
public class FarmBlock extends BaseEntityBlock {
	public static final MapCodec<FarmBlock> CODEC = simpleCodec(FarmBlock::new);
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	/** Legs at the corners carrying a tray of soil, which is the model with the crops left out. */
	private static final VoxelShape SHAPE = Shapes.or(
			Block.box(0.0, 0.0, 0.0, 2.0, 10.0, 2.0),
			Block.box(14.0, 0.0, 0.0, 16.0, 10.0, 2.0),
			Block.box(0.0, 0.0, 14.0, 2.0, 10.0, 16.0),
			Block.box(14.0, 0.0, 14.0, 16.0, 10.0, 16.0),
			Block.box(0.0, 10.0, 0.0, 16.0, 14.0, 16.0));

	public FarmBlock(Properties settings) {
		super(settings);
		registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected MapCodec<? extends FarmBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Nullable
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext ctx) {
		return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
	}

	@Override
	public BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	public BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	/** Kept out of planned paths for the reason {@link RanchBlock} spells out: legs are not a cube. */
	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType type) {
		return false;
	}

	@Override
	public RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new FarmBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type) {
		if (world.isClientSide()) {
			return null;
		}

		return createTickerHelper(type, WorkstationsMod.FARM_BLOCK_ENTITY.get(), WorkStationBlockEntity::serverTick);
	}

	@Override
	public void setPlacedBy(Level world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
		super.setPlacedBy(world, pos, state, placer, itemStack);

		if (!(world instanceof ServerLevel serverWorld)
				|| !(world.getBlockEntity(pos) instanceof FarmBlockEntity station)) {
			return;
		}

		// Both done here rather than left to the tick timer, so placing the block visibly does
		// something and the player is told straight away how much field it found.
		int plots = station.registerPlots(serverWorld);
		station.summonWorker(serverWorld);

		if (placer instanceof Player player) {
			player.sendOverlayMessage(Component.translatable("message.villager_workstations.farm_placed", plots));
		}
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
		if (world.isClientSide()) {
			return InteractionResult.SUCCESS;
		}

		if (!(world.getBlockEntity(pos) instanceof FarmBlockEntity)) {
			return InteractionResult.PASS;
		}

		MenuProvider factory = state.getMenuProvider(world, pos);

		if (factory != null) {
			player.openMenu(factory);
		}

		return InteractionResult.CONSUME;
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel world, BlockPos pos, boolean moved) {
		Containers.updateNeighboursAfterDestroy(state, world, pos);
	}

	@Override
	public boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	public int getAnalogOutputSignal(BlockState state, Level world, BlockPos pos, Direction direction) {
		return AbstractContainerMenu.getRedstoneSignalFromBlockEntity(world.getBlockEntity(pos));
	}
}
