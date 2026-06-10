package net.borisshoes.borislib.sequences;

import net.minecraft.world.phys.Vec3;

/**
 * The result of evaluating a {@link CameraPath} at a specific normalised time.
 * Contains the interpolated camera eye position and look angles.
 *
 * @param position Camera eye position in world space.
 * @param yaw      Horizontal look angle in degrees (−180 … 180).
 * @param pitch    Vertical look angle in degrees (−90 … 90).
 */
public record CameraPathSample(Vec3 position, float yaw, float pitch) {
   public static final CameraPathSample ZERO = new CameraPathSample(Vec3.ZERO, 0f, 0f);
}

