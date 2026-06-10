package net.borisshoes.borislib.sequences;

import net.minecraft.world.phys.Vec3;

/**
 * A single keyframe in a {@link CameraPath}.
 * <p>
 * The {@code positionInterp} and {@code rotationInterp} fields control the interpolation
 * mode used when transitioning <em>from the previous keyframe to this one</em>.
 * For the very first keyframe these values are ignored.
 *
 * <p>Build instances with the fluent {@link #at(double)} builder:
 * <pre>{@code
 * CameraKeyframe kf = CameraKeyframe.at(0.5)
 *     .pos(100, 64, 200)
 *     .rot(-45, 10)
 *     .interp(InterpolationType.EASE_IN_OUT)
 *     .build();
 * }</pre>
 *
 * @param time           Normalised time in [0.0, 1.0] at which this keyframe occurs.
 * @param position       Camera eye world position at this keyframe.
 * @param yaw            Horizontal look angle in degrees (−180 … 180).
 * @param pitch          Vertical look angle in degrees (−90 … 90).
 * @param positionInterp Interpolation mode for the <em>position</em> segment leading into this keyframe.
 * @param rotationInterp Interpolation mode for the <em>rotation</em> segment leading into this keyframe.
 */
public record CameraKeyframe(
      double time,
      Vec3 position,
      float yaw,
      float pitch,
      InterpolationType positionInterp,
      InterpolationType rotationInterp
) {
   
   /**
    * Start building a keyframe at the given normalised time (0.0–1.0).
    */
   public static Builder at(double time){
      return new Builder(time);
   }
   
   // ────────────────────────────────────────────────────────────────────────
   
   public static final class Builder {
      private final double time;
      private Vec3 pos = Vec3.ZERO;
      private float yaw = 0f;
      private float pitch = 0f;
      private InterpolationType posInterp = InterpolationType.LINEAR;
      private InterpolationType rotInterp = InterpolationType.LINEAR;
      
      private Builder(double time){
         this.time = time;
      }
      
      /**
       * Set the camera eye position via components.
       */
      public Builder pos(double x, double y, double z){
         this.pos = new Vec3(x, y, z);
         return this;
      }
      
      /**
       * Set the camera eye position via a {@link Vec3}.
       */
      public Builder pos(Vec3 pos){
         this.pos = pos;
         return this;
      }
      
      /**
       * Set the camera look direction.
       */
      public Builder rot(float yaw, float pitch){
         this.yaw = yaw;
         this.pitch = pitch;
         return this;
      }
      
      /**
       * Set <em>both</em> position and rotation interpolation to the same type.
       */
      public Builder interp(InterpolationType type){
         this.posInterp = type;
         this.rotInterp = type;
         return this;
      }
      
      /**
       * Set only the position interpolation.
       */
      public Builder posInterp(InterpolationType type){
         this.posInterp = type;
         return this;
      }
      
      /**
       * Set only the rotation interpolation.
       */
      public Builder rotInterp(InterpolationType type){
         this.rotInterp = type;
         return this;
      }
      
      public CameraKeyframe build(){
         return new CameraKeyframe(time, pos, yaw, pitch, posInterp, rotInterp);
      }
   }
}

