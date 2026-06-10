package net.borisshoes.borislib.sequences;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A time-parameterised camera path built from {@link CameraKeyframe keyframes}.
 * <p>
 * Supports independent interpolation modes (linear, ease curves, Catmull-Rom spline,
 * step) for both position and rotation on a per-keyframe basis.
 *
 * <p>Build via {@link #builder()} and evaluate via {@link #evaluate(double)} using a
 * normalised time value in {@code [0, 1]}.
 *
 * <p>Example:
 * <pre>{@code
 * CameraPath path = CameraPath.builder()
 *     .add(CameraKeyframe.at(0.0).pos(0, 65, 0).rot(0, 0).build())
 *     .add(CameraKeyframe.at(0.5).pos(10, 66, 10).rot(45, -5).interp(InterpolationType.EASE_IN_OUT).build())
 *     .add(CameraKeyframe.at(1.0).pos(20, 65, 0).rot(90, 0).interp(InterpolationType.CUBIC).build())
 *     .build();
 *
 * CameraPathSample s = path.evaluate(0.25); // 25% through the path
 * }</pre>
 */
public final class CameraPath {
   
   /**
    * Keyframes sorted ascending by time. Immutable after construction.
    */
   private final List<CameraKeyframe> keyframes;
   
   private CameraPath(List<CameraKeyframe> sorted){
      this.keyframes = List.copyOf(sorted);
   }
   
   // ─────────────────────────────── evaluate ────────────────────────────────
   
   /**
    * Evaluates the path at the given normalised time {@code t}, clamped to [0, 1].
    * Returns a {@link CameraPathSample} with the interpolated position, yaw, and pitch.
    */
   public CameraPathSample evaluate(double t){
      t = Mth.clamp(t, 0.0, 1.0);
      int size = keyframes.size();
      
      if(size == 0) return CameraPathSample.ZERO;
      
      if(size == 1){
         CameraKeyframe k = keyframes.get(0);
         return new CameraPathSample(k.position(), k.yaw(), k.pitch());
      }
      
      // Clamp to first / last keyframe outside their range
      if(t <= keyframes.get(0).time()){
         CameraKeyframe k = keyframes.get(0);
         return new CameraPathSample(k.position(), k.yaw(), k.pitch());
      }
      if(t >= keyframes.get(size - 1).time()){
         CameraKeyframe k = keyframes.get(size - 1);
         return new CameraPathSample(k.position(), k.yaw(), k.pitch());
      }
      
      // Locate surrounding keyframe segment
      int idx = 0;
      for(int i = 0; i < size - 1; i++){
         if(t >= keyframes.get(i).time() && t < keyframes.get(i + 1).time()){
            idx = i;
            break;
         }
      }
      
      CameraKeyframe k0 = keyframes.get(idx);
      CameraKeyframe k1 = keyframes.get(idx + 1);
      double segLen = k1.time() - k0.time();
      double s = segLen < 1e-10 ? 0.0 : (t - k0.time()) / segLen; // local [0,1] within segment
      
      // ── Position ──────────────────────────────────────────────────────────
      Vec3 pos;
      InterpolationType posMode = k1.positionInterp();
      if(posMode == InterpolationType.STEP){
         // Hold at the previous keyframe's position for the entire segment;
         // position snaps to k1 at the keyframe boundary (when evaluation
         // moves into the next segment or clamps to the last keyframe).
         pos = k0.position();
      }else if(posMode == InterpolationType.CUBIC){
         CameraKeyframe kPrev = idx > 0 ? keyframes.get(idx - 1) : k0;
         CameraKeyframe kNext = (idx + 2 < size) ? keyframes.get(idx + 2) : k1;
         pos = catmullRom(kPrev.position(), k0.position(), k1.position(), kNext.position(), s);
      }else{
         pos = lerpVec(k0.position(), k1.position(), applyEase(s, posMode));
      }
      
      // ── Rotation ──────────────────────────────────────────────────────────
      InterpolationType rotMode = k1.rotationInterp();
      float yaw, pitch;
      if(rotMode == InterpolationType.STEP){
         yaw = k0.yaw();
         pitch = k0.pitch();
      }else{
         double rotS = applyEase(s, rotMode);
         yaw = lerpAngle(k0.yaw(), k1.yaw(), (float) rotS);
         pitch = Mth.lerp((float) rotS, k0.pitch(), k1.pitch());
      }
      
      return new CameraPathSample(pos, yaw, pitch);
   }
   
   // ─────────────────────────────── helpers ─────────────────────────────────
   
   /**
    * Apply the easing function for the given interpolation mode to local time {@code s}.
    */
   private static double applyEase(double s, InterpolationType mode){
      return switch(mode){
         case LINEAR, CUBIC, STEP -> s;
         case EASE_IN -> s * s * s;
         case EASE_OUT -> 1.0 - Math.pow(1.0 - s, 3.0);
         case EASE_IN_OUT -> s < 0.5
               ? 4.0 * s * s * s
               : 1.0 - Math.pow(-2.0 * s + 2.0, 3.0) / 2.0;
      };
   }
   
   /**
    * Catmull-Rom spline through {@code p1} → {@code p2}, using {@code p0} and {@code p3}
    * as ghost control points. {@code t=0} produces {@code p1}, {@code t=1} produces {@code p2}.
    */
   private static Vec3 catmullRom(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double t){
      double t2 = t * t;
      double t3 = t2 * t;
      double x = 0.5 * ((2 * p1.x) + (-p0.x + p2.x) * t
            + (2 * p0.x - 5 * p1.x + 4 * p2.x - p3.x) * t2
            + (-p0.x + 3 * p1.x - 3 * p2.x + p3.x) * t3);
      double y = 0.5 * ((2 * p1.y) + (-p0.y + p2.y) * t
            + (2 * p0.y - 5 * p1.y + 4 * p2.y - p3.y) * t2
            + (-p0.y + 3 * p1.y - 3 * p2.y + p3.y) * t3);
      double z = 0.5 * ((2 * p1.z) + (-p0.z + p2.z) * t
            + (2 * p0.z - 5 * p1.z + 4 * p2.z - p3.z) * t2
            + (-p0.z + 3 * p1.z - 3 * p2.z + p3.z) * t3);
      return new Vec3(x, y, z);
   }
   
   private static Vec3 lerpVec(Vec3 a, Vec3 b, double t){
      return new Vec3(
            a.x + (b.x - a.x) * t,
            a.y + (b.y - a.y) * t,
            a.z + (b.z - a.z) * t);
   }
   
   /**
    * Lerps between two angles (degrees), always taking the shortest arc
    * (handles 360°/0° wrap-around correctly).
    */
   private static float lerpAngle(float from, float to, float t){
      float diff = ((to - from) % 360f + 540f) % 360f - 180f;
      return from + diff * t;
   }
   
   // ─────────────────────────────── accessors ───────────────────────────────
   
   /**
    * Returns the immutable, time-sorted list of keyframes.
    */
   public List<CameraKeyframe> getKeyframes(){
      return keyframes;
   }
   
   // ─────────────────────────────── builder ─────────────────────────────────
   
   public static Builder builder(){
      return new Builder();
   }
   
   /**
    * Creates a simple two-keyframe path representing a static, motionless camera view.
    *
    * @param position Camera eye position (world space).
    * @param yaw      Horizontal look angle in degrees.
    * @param pitch    Vertical look angle in degrees.
    */
   public static CameraPath staticView(Vec3 position, float yaw, float pitch){
      return builder()
            .add(CameraKeyframe.at(0.0).pos(position).rot(yaw, pitch).build())
            .add(CameraKeyframe.at(1.0).pos(position).rot(yaw, pitch).build())
            .build();
   }
   
   public static final class Builder {
      private final List<CameraKeyframe> keyframes = new ArrayList<>();
      
      /**
       * Add a pre-built keyframe.
       */
      public Builder add(CameraKeyframe kf){
         keyframes.add(kf);
         return this;
      }
      
      /**
       * Convenience: add a LINEAR keyframe.
       */
      public Builder add(double time, Vec3 pos, float yaw, float pitch){
         return add(CameraKeyframe.at(time).pos(pos).rot(yaw, pitch).build());
      }
      
      /**
       * Convenience: add a keyframe with an explicit shared interpolation type.
       */
      public Builder add(double time, Vec3 pos, float yaw, float pitch, InterpolationType interp){
         return add(CameraKeyframe.at(time).pos(pos).rot(yaw, pitch).interp(interp).build());
      }
      
      public CameraPath build(){
         if(keyframes.isEmpty())
            throw new IllegalStateException("CameraPath requires at least one keyframe");
         return new CameraPath(
               keyframes.stream()
                     .sorted(Comparator.comparingDouble(CameraKeyframe::time))
                     .toList());
      }
   }
}

