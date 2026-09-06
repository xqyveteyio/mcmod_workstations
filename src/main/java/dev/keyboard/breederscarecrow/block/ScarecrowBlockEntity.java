package dev.keyboard.breederscarecrow.block;

import dev.keyboard.breederscarecrow.BreederScarecrowMod;
import dev.keyboard.breederscarecrow.ModConfig;
import dev.keyboard.breederscarecrow.breeding.BreedingManager;
import dev.keyboard.breederscarecrow.pen.PenRegion;
import dev.keyboard.breederscarecrow.pen.PenScanner;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class ScarecrowBlockEntity extends LootableContainerBlockEntity {
	public static final int INVENTORY_SIZE = 27;
	private static final String REGION_KEY = "Pen";

	private DefaultedList<ItemStack> inventory = DefaultedList.ofSize(INVENTORY_SIZE, ItemStack.EMPTY);
	private PenRegion region = PenRegion.empty(0);
	private int rescanTimer;
	private int breedTimer;

	public ScarecrowBlockEntity(BlockPos pos, BlockState state) {
		super(BreederScarecrowMod.SCARECROW_BLOCK_ENTITY, pos, state);
	}

	public static void serverTick(World world, BlockPos pos, BlockState state, ScarecrowBlockEntity blockEntity) {
		if (!(world instanceof ServerWorld serverWorld)) {
			return;
		}

		ModConfig config = ModConfig.get();

		if (--blockEntity.rescanTimer <= 0) {
			blockEntity.rescanTimer = config.rescanIntervalTicks;
			blockEntity.rescan();
		}

		if (--blockEntity.breedTimer <= 0) {
			blockEntity.breedTimer = config.breedIntervalTicks;

			if (blockEntity.region.isEnclosed()) {
				BreedingManager.run(serverWorld, blockEntity.region, blockEntity);
			}
		}
	}

	/** Makes the next server tick re-scan, used when a block near the pen changed. */
	public void requestRescan() {
		rescanTimer = 0;
	}

	/** Re-runs the flood fill and syncs the result to nearby clients when it changed. */
	public PenRegion rescan() {
		if (world == null || world.isClient) {
			return region;
		}

		setRegion(PenScanner.scan(world, pos));
		return region;
	}

	public PenRegion getPenRegion() {
		return region;
	}

	private void setRegion(PenRegion newRegion) {
		if (newRegion.equals(region)) {
			return;
		}

		region = newRegion;
		markDirty();

		if (world != null) {
			world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
		}
	}

	@Override
	public int size() {
		return INVENTORY_SIZE;
	}

	@Override
	protected DefaultedList<ItemStack> getInvStackList() {
		return inventory;
	}

	@Override
	protected void setInvStackList(DefaultedList<ItemStack> list) {
		inventory = list;
	}

	@Override
	protected Text getContainerName() {
		return Text.translatable("container.breeder_scarecrow.scarecrow");
	}

	@Override
	protected ScreenHandler createScreenHandler(int syncId, PlayerInventory playerInventory) {
		return GenericContainerScreenHandler.createGeneric9x3(syncId, playerInventory, this);
	}

	@Override
	protected void writeNbt(NbtCompound nbt) {
		super.writeNbt(nbt);

		if (!serializeLootTable(nbt)) {
			Inventories.writeNbt(nbt, inventory);
		}

		nbt.put(REGION_KEY, region.writeNbt());
	}

	@Override
	public void readNbt(NbtCompound nbt) {
		super.readNbt(nbt);
		inventory = DefaultedList.ofSize(INVENTORY_SIZE, ItemStack.EMPTY);

		if (!deserializeLootTable(nbt)) {
			Inventories.readNbt(nbt, inventory);
		}

		if (nbt.contains(REGION_KEY, NbtElement.COMPOUND_TYPE)) {
			region = PenRegion.fromNbt(nbt.getCompound(REGION_KEY));
		}
	}

	/** Clients only need the pen shape, not the feed stored inside. */
	@Override
	public NbtCompound toInitialChunkDataNbt() {
		NbtCompound nbt = new NbtCompound();
		nbt.put(REGION_KEY, region.writeNbt());
		return nbt;
	}

	@Nullable
	@Override
	public Packet<ClientPlayPacketListener> toUpdatePacket() {
		return BlockEntityUpdateS2CPacket.create(this, BlockEntity::toInitialChunkDataNbt);
	}
}
