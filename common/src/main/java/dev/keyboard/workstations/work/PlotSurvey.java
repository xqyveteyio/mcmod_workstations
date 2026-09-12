package dev.keyboard.workstations.work;

import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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
	private final List<BlockPos> growing = new ArrayList<>();
	private final List<BlockPos> ripe = new ArrayList<>();
	private final List<BlockPos> lost = new ArrayList<>();

	private PlotSurvey() {
	}

	public static PlotSurvey of(ServerWorld world, Collection<BlockPos> plots) {
		PlotSurvey survey = new PlotSurvey();

		for (BlockPos plot : plots) {
			// An unloaded plot is not judged either way: reading it would load the chunk, and
			// striking it off because it happens to be out of sight would lose the field.
			if (!world.isChunkLoaded(plot.getX() >> 4, plot.getZ() >> 4)) {
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

			if (Crops.isGrowing(world, plot)) {
				survey.growing.add(plot);
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

	/** Plots with a crop that is not yet ripe, which is what bone meal is aimed at. */
	public List<BlockPos> growing() {
		return growing;
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
