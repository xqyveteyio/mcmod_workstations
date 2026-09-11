package dev.keyboard.workstations.client;

import dev.keyboard.workstations.work.WorkerSkin;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;

/** Extra drawing data a worker needs on top of a villager's usual pose. */
public class WorkerRenderState extends VillagerRenderState {
	public WorkerSkin skin;
	public double sink;
	public boolean buried;
}
