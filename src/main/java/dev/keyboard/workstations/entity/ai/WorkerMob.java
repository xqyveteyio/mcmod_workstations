package dev.keyboard.workstations.entity.ai;

/**
 * What the shared navigation and gate handling need to know about a worker, whatever kind of work
 * it does. Both a ranch and a field can be fenced, so both workers want gates that open.
 */
public interface WorkerMob {
	/** Whether this worker's station lets it work fence gates. */
	boolean mayOpenGates();
}
