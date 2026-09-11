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

public class RanchBlock extends BaseEntityBlock {
	public static final MapCodec<RanchBlock> CODEC = simpleCodec(RanchBlock::new);
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	/**
	 * Matches the table model: legs at the corners, the top they carry, and the miniature fence
	 * ringing it. The fence is four walls rather than one lid, so the pen holding the display
	 * animals stays as open as it looks.
	 */
	private static final VoxelShape SHAPE = Shapes.or(
			Block.box(0.0, 0.0, 0.0, 2.0, 10.0, 2.0),
			Block.box(14.0, 0.0, 0.0, 16.0, 10.0, 2.0),
			Block.box(0.0, 0.0, 14.0, 2.0, 10.0, 16.0),
			Block.box(14.0, 0.0, 14.0, 16.0, 10.0, 16.0),
			Block.box(0.0, 10.0, 0.0, 16.0, 12.0, 16.0),
			Block.box(0.0, 12.0, 0.0, 16.0, 16.0, 1.0),
			Block.box(0.0, 12.0, 15.0, 16.0, 16.0, 16.0),
			Block.box(0.0, 12.0, 1.0, 1.0, 16.0, 15.0),
			Block.box(15.0, 12.0, 1.0, 16.0, 16.0, 15.0));

	public RanchBlock(Properties settings) {
		super(settings);
		registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected MapCodec<? extends RanchBlock> codec() {
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

	/**
	 * Kept out of every path the game plans. Vanilla decides a block may be walked through from
	 * whether its collision box fills the cube, and a table standing on legs does not fill it, so
	 * without this the station reads as open ground: the rancher coming home to unload is routed
	 * straight through its own station and then stands wedged against the tabletop, which is solid
	 * and is the one part of the shape the path never accounted for.
	 */
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
		return new RanchBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type) {
		if (world.isClientSide()) {
			return null;
		}

		return createTickerHelper(type, WorkstationsMod.RANCH_BLOCK_ENTITY, WorkStationBlockEntity::serverTick);
	}

	@Override
	public void setPlacedBy(Level world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
		super.setPlacedBy(world, pos, state, placer, itemStack);

		if (!(world instanceof ServerLevel serverWorld)
				|| !(world.getBlockEntity(pos) instanceof RanchBlockEntity station)) {
			return;
		}

		// Summoned here rather than waiting on the tick timer, so placing the block visibly does something.
		station.summonWorker(serverWorld);

		if (placer instanceof Player player) {
			player.sendOverlayMessage(Component.translatable("message.keyboard_workstations.station_placed",
					station.getSettings().workAlong, station.getSettings().workAcross,
					station.getSettings().workAbove, station.getSettings().workBelow));
		}
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
		if (world.isClientSide()) {
			return InteractionResult.SUCCESS;
		}

		if (!(world.getBlockEntity(pos) instanceof RanchBlockEntity station)) {
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
