package dev.keyboard.workstations.block;

/**
 * A lid whose swing is drawn by the client. Kept as our own type so the renderer does not have to
 * name vanilla's {@code LidOpenable}, which 1.16 never had.
 */
public interface AnimatedLid {
	float getAnimationProgress(float tickDelta);
}
