package dev.keyboard.workstations.client;

import net.minecraft.client.util.math.MatrixStack;
//? if >=1.19.3 {
import net.minecraft.util.math.RotationAxis;
//?} else {
/* import net.minecraft.util.math.Vec3f; */
//?}

/** Yarn names that moved in the drawing code between the versions this project builds. */
final class Draw {
	private Draw() {
	}

	static void yaw(MatrixStack matrices, float degrees) {
		//? if >=1.19.3 {
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(degrees));
		//?} else {
		/* matrices.multiply(Vec3f.POSITIVE_Y.getDegreesQuaternion(degrees)); */
		//?}
	}
}
