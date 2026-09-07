package dev.keyboard.workstations.block;

import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * The little a station needs to know about its worker: where it thinks it works. Enough for a
 * station to recognise its own worker among strays and to keep telling it where home is, without
 * the station having to know what kind of work that worker does.
 */
public interface StationWorker {
	@Nullable
	BlockPos getStationPos();

	void setStation(BlockPos pos);
}
