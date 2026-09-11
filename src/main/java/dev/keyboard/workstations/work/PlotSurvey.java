package dev.keyboard.workstations.work;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One look at every plot a farm station has on its books, sorted into what needs doing to it.
 *
 * <p>Only registered positions are examined, never the whole work area, so a survey costs one
 * block lookup per plot however large the area is set. Positions that are no longer plots at all
 * come back in {@link #lost()} for the station to strike off its register.
 */
public final class PlotSurvey {
	private final List<BlockPos> tillable = new ArrayList<>();
	private final List<BlockPos> bare = new ArrayList<>();
	private final List<BlockPos> ripe = new ArrayList<>();
	private final List<BlockPos> lost = new ArrayList<>();

	private PlotSurvey() {
	}

	public static PlotSurvey of(ServerLevel world, Collection<BlockPos> plots) {
		PlotSurvey survey = new PlotSurvey();

		for (BlockPos plot : plots) {
			// An unloaded plot is not judged either way: reading it would load the chunk, and
			// striking it off because it happens to be out of sight would lose the field.
			if (!world.hasChunk(plot.getX() >> 4, plot.getZ() >> 4)) {
				continue;
			}

			BlockState state = world.getBlockState(plot);

			if (!Crops.isPlot(state)) {
				survey.lost.add(plot);
				continue;
			}

			if (Crops.isRipe(world, plot)) {
				survey.ripe.add(plot);
				continue;
			}

			// Something is growing but is not done yet, so there is nothing to do here at all.
			if (Crops.growingOn(world, plot) != null) {
				continue;
			}

			if (!Crops.isClearAbove(world, plot)) {
				continue;
			}

			if (Crops.isTillable(state)) {
				survey.tillable.add(plot);
			} else {
				survey.bare.add(plot);
			}
		}

		return survey;
	}

	/** Plots that got trampled or were never hoed, waiting to be turned back into farmland. */
	public List<BlockPos> tillable() {
		return tillable;
	}

	/** Farmland standing empty, waiting for a seed. */
	public List<BlockPos> bare() {
		return bare;
	}

	/** Plots whose crop has finished growing. */
	public List<BlockPos> ripe() {
		return ripe;
	}

	/** Positions that are no longer farmland or soil, and so are no longer the farm's business. */
	public List<BlockPos> lost() {
		return lost;
	}
}
