package net.borisshoes.borislib.sequences;

/**
 * Interpolation modes for {@link CameraKeyframe} transitions in a {@link CameraPath}.
 * <p>
 * Each keyframe's {@code positionInterp} and {@code rotationInterp} fields control
 * how the path transitions <em>from the previous keyframe into this one</em>.
 * The first keyframe's interpolation type is therefore ignored.
 */
public enum InterpolationType {
   
   /**
    * Constant velocity from one keyframe to the next.
    */
   LINEAR,
   
   /**
    * Starts slow, accelerates toward the end (cubic ease-in: t³).
    */
   EASE_IN,
   
   /**
    * Starts fast, decelerates toward the end (cubic ease-out: 1-(1-t)³).
    */
   EASE_OUT,
   
   /**
    * Smooth S-curve: slow start, fast middle, slow end.
    * (Cubic ease-in-out: 4t³ for t&lt;0.5, 1 - (-2t+2)³/2 for t≥0.5)
    */
   EASE_IN_OUT,
   
   /**
    * Catmull-Rom spline interpolation.
    * Uses the surrounding keyframes as tangent control points, producing
    * naturally smooth curves that pass exactly through every keyframe.
    * Falls back to {@link #LINEAR} when fewer than 2 keyframes are present.
    */
   CUBIC,
   
   /**
    * No interpolation — stays at the previous keyframe's value until the
    * very end of the segment, then immediately snaps to the next value.
    */
   STEP
}

