package net.borisshoes.borislib.conditions;

import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.List;

/**
 * Pure math helpers for the condition magnitude model.
 *
 * <p>Every condition value is a <em>magnitude</em> in {@code [0, cap]}: {@code 0} is absent, larger is
 * stronger, and {@code +∞} is available to conditions that opt in. Multiplicative conditions map a
 * magnitude onto a factor through one of two curves:</p>
 * <ul>
 *    <li>{@link #amplify(float) Amplify}: {@code 1 + m}</li>
 *    <li>{@link #attenuate(float) Attenuate}: {@code 1 / (1 + m)}</li>
 * </ul>
 * <p>Equal magnitudes of an opposing pair cancel exactly. Instances can also be written as percentages of
 * the effect instead of magnitudes; see {@link Scale} and {@link #combine(List, float, Scale)}. Nothing in
 * this class touches the server, so it can be unit tested without a running game.</p>
 */
public final class ConditionMath {

   private ConditionMath(){
   }

   /**
    * The minimal view of a {@link ConditionInstance} that {@link #combine(List, float, Scale)} needs. Exists so the
    * combine rules can be exercised without registries.
    */
   public interface Term {
      /** @return this term's value contribution. */
      float getValue();

      /** @return the operation used when combining this term into the stacked total. */
      AttributeModifier.Operation getOperation();

      /** @return {@code true} if this term contributes to the stacked sum. */
      boolean isStacking();
   }

   /**
    * Amplify curve.
    *
    * @param m the magnitude
    * @return {@code 1 + m}; {@code +∞} for an infinite magnitude
    */
   public static float amplify(float m){
      return 1.0f + m;
   }

   /**
    * Attenuate curve.
    *
    * @param m the magnitude
    * @return {@code 1 / (1 + m)}; {@code 0} for an infinite magnitude
    */
   public static float attenuate(float m){
      if(m == Float.POSITIVE_INFINITY) return 0.0f;
      return 1.0f / (1.0f + m);
   }

   /**
    * Combined factor of an amplifier and an attenuator acting on the same quantity. Never returns
    * {@code NaN}: an infinite attenuator always wins.
    *
    * @param amp the amplifying magnitude
    * @param att the attenuating magnitude
    * @return {@code 0} if {@code att} is infinite, otherwise {@code +∞} if {@code amp} is infinite,
    *         otherwise {@code (1 + amp) / (1 + att)}
    */
   public static float ratio(float amp, float att){
      if(att == Float.POSITIVE_INFINITY) return 0.0f;
      if(amp == Float.POSITIVE_INFINITY) return Float.POSITIVE_INFINITY;
      return (1.0f + amp) / (1.0f + att);
   }

   /**
    * Multiplies two factors with {@code 0 × ∞ = 0}, so attenuation wins over amplification.
    *
    * @param a the first factor
    * @param b the second factor
    * @return the product, never {@code NaN} for non-{@code NaN} inputs
    */
   public static float multiplyFactors(float a, float b){
      if(a == 0.0f || b == 0.0f) return 0.0f;
      return a * b;
   }

   /**
    * Converts an amplifying factor into the magnitude that produces it.
    *
    * @param factor the desired multiplier, {@code >= 1}
    * @return {@code factor - 1}; {@code +∞} for an infinite factor
    * @throws IllegalArgumentException if {@code factor} is below 1 or {@code NaN}
    */
   public static float amplifyToMagnitude(float factor){
      if(!(factor >= 1.0f)) throw new IllegalArgumentException("Amplify factor must be >= 1, got " + factor);
      return factor - 1.0f;
   }

   /**
    * Converts a fractional reduction into the attenuating magnitude that produces it. {@code 0.5} gives
    * {@code 1}, {@code 0.75} gives {@code 3} and {@code 1.0} gives {@code +∞}.
    *
    * @param fraction the fraction removed, in {@code [0, 1]}
    * @return {@code fraction / (1 - fraction)}; {@code +∞} for exactly 1
    * @throws IllegalArgumentException if {@code fraction} is outside {@code [0, 1]} or {@code NaN}
    */
   public static float reductionToMagnitude(float fraction){
      if(!(fraction >= 0.0f && fraction <= 1.0f)) throw new IllegalArgumentException("Reduction fraction must be in [0, 1], got " + fraction);
      if(fraction == 1.0f) return Float.POSITIVE_INFINITY;
      return fraction / (1.0f - fraction);
   }

   /**
    * Inverse of {@link #reductionToMagnitude(float)}, intended for display.
    *
    * @param m the attenuating magnitude
    * @return the fraction removed, {@code m / (1 + m)}; {@code 1} for an infinite magnitude
    */
   public static float magnitudeToReduction(float m){
      if(m == Float.POSITIVE_INFINITY) return 1.0f;
      return m / (1.0f + m);
   }

   /**
    * Converts a desired gravity factor into a Levity magnitude. {@code 0.5} is half gravity, {@code 0} is
    * weightless, {@code -1} is reversed at normal strength and {@code -2.5} is reversed at 2.5×.
    *
    * @param f the desired gravity factor, at most 1
    * @return {@code 1 - f}
    * @throws IllegalArgumentException if {@code f} is above 1 or {@code NaN}
    */
   public static float levityForGravityFactor(float f){
      if(!(f <= 1.0f)) throw new IllegalArgumentException("Gravity factor must be <= 1, got " + f);
      return 1.0f - f;
   }

   /**
    * How a condition's magnitude {@code m} turns into its effect, which decides what a <em>percentage</em>
    * of that condition means. {@code ADD_VALUE} instances always add magnitude; the two multiplied
    * operations work in percent of the effect, on this scale.
    */
   public enum Scale {
      /**
       * The magnitude is a plain quantity (health per second, an intensity). Percentages scale the
       * magnitude itself, exactly like vanilla attribute modifiers, so they do nothing without an
       * {@code ADD_VALUE} instance to scale.
       */
      LINEAR,
      /**
       * The effect is multiplied by {@code 1 + m}. A percentage {@code p} is an increase of the effect:
       * {@code ADD_MULTIPLIED_BASE} percentages add up ({@code +15%} and {@code +20%} give {@code +35%})
       * and {@code ADD_MULTIPLIED_TOTAL} percentages compound ({@code ×1.15 × 1.20}).
       */
      AMPLIFY,
      /**
       * The effect is multiplied by {@code 1 / (1 + m)}. A percentage {@code p} is a reduction of the
       * effect: {@code ADD_MULTIPLIED_BASE} percentages add up ({@code 15%} and {@code 20%} give
       * {@code 35%}) and {@code ADD_MULTIPLIED_TOTAL} percentages compound ({@code ×0.85 × 0.80}).
       * Reaching a 100% reduction gives the condition's cap.
       */
      ATTENUATE
   }

   /**
    * Combines every instance of one condition type into its prevailing magnitude.
    *
    * @param instances instances of one condition type
    * @param condition the condition type, which supplies the cap and the scale
    * @return the prevailing state; its value is {@code 0} when nothing is in effect
    * @see #combine(List, float, Scale)
    */
   public static PrevailingCondition combine(List<ConditionInstance> instances, Condition condition){
      return combine(instances, condition.getCap(), condition.getScale());
   }

   /**
    * Combines terms of one condition type into a prevailing magnitude, in a single pass.
    *
    * <p>Stacking terms are gathered by operation: {@code A} is the sum of the {@code ADD_VALUE} values,
    * {@code S} the sum of the {@code ADD_MULTIPLIED_BASE} values and {@code t} each
    * {@code ADD_MULTIPLIED_TOTAL} value. They are folded in vanilla attribute order (adds, then base
    * percentages, then total percentages) on the condition's {@link Scale}:</p>
    * <ul>
    *    <li>{@link Scale#LINEAR}: {@code m = A × (1 + S) × Π(1 + t)}</li>
    *    <li>{@link Scale#AMPLIFY}: {@code 1 + m = (1 + A + S) × Π(1 + t)}</li>
    *    <li>{@link Scale#ATTENUATE}: {@code 1 / (1 + m) = (1 / (1 + A) − S) × Π(1 − t)}</li>
    * </ul>
    * <p>On the two factor scales a base percentage is a share of the unmodified effect, so it is worth the
    * same whatever else is present, while a total percentage is a share of whatever is left.</p>
    * <p>With only {@code ADD_VALUE} terms every scale reduces to {@code m = A}. On the two factor scales
    * each bracket is floored at 0, so over-100% percentages saturate instead of flipping sign; an
    * attenuation that reaches 100% gives the cap. {@code 0 × ∞} is defined as {@code 0}, and an infinite
    * {@code A} is absorbing: no percentage can cancel it. The stacked result is clamped to
    * {@code [0, cap]}.</p>
    *
    * <p>Each non-stacking term is a candidate: the magnitude it would give on its own. The result is the
    * larger of the stacked value and the best candidate, with the stacked value winning ties.</p>
    *
    * @param terms terms of one condition type
    * @param cap   the largest permitted magnitude
    * @param scale how the condition's magnitude turns into its effect
    * @return the prevailing state; its value is {@code 0} when nothing is in effect
    */
   public static PrevailingCondition combine(List<? extends Term> terms, float cap, Scale scale){
      double addSum = 0.0;
      double baseSum = 0.0;
      double totalProduct = 1.0;
      float bestNonStacking = 0.0f;

      for(int i = 0; i < terms.size(); i++){
         Term term = terms.get(i);
         float v = term.getValue();
         if(!term.isStacking()){
            float candidate = switch(term.getOperation()){
               case ADD_VALUE -> resolve(v, 0.0, 1.0, cap, scale);
               case ADD_MULTIPLIED_BASE -> resolve(0.0, v, 1.0, cap, scale);
               case ADD_MULTIPLIED_TOTAL -> resolve(0.0, 0.0, totalFactor(v, scale), cap, scale);
            };
            if(candidate > bestNonStacking) bestNonStacking = candidate;
            continue;
         }
         switch(term.getOperation()){
            case ADD_VALUE -> addSum += v;
            case ADD_MULTIPLIED_BASE -> baseSum += v;
            case ADD_MULTIPLIED_TOTAL -> totalProduct = multiply(totalProduct, totalFactor(v, scale));
         }
      }

      float stacked = resolve(addSum, baseSum, totalProduct, cap, scale);
      if(bestNonStacking > stacked) return new PrevailingCondition(bestNonStacking, false);
      return new PrevailingCondition(stacked, true);
   }

   /** The factor one {@code ADD_MULTIPLIED_TOTAL} value contributes to the running product. */
   private static double totalFactor(float t, Scale scale){
      return switch(scale){
         case LINEAR -> 1.0 + t;
         case AMPLIFY -> Math.max(0.0, 1.0 + t);
         case ATTENUATE -> Math.max(0.0, 1.0 - t);
      };
   }

   private static float resolve(double addSum, double baseSum, double totalProduct, float cap, Scale scale){
      // Infinity is absorbing, and plain adds are already a magnitude on every scale
      if(addSum == Double.POSITIVE_INFINITY || (baseSum == 0.0 && totalProduct == 1.0)) return clampMagnitude(addSum, cap);
      return switch(scale){
         case LINEAR -> {
            double b = addSum + multiply(addSum, baseSum);
            yield clampMagnitude(b == Double.POSITIVE_INFINITY ? b : multiply(b, totalProduct), cap);
         }
         case AMPLIFY -> clampMagnitude(multiply(Math.max(0.0, 1.0 + addSum + baseSum), totalProduct) - 1.0, cap);
         case ATTENUATE -> {
            // What the magnitudes leave of the effect, less the base percentages, times what each total percentage leaves
            double afterAdds = addSum <= -1.0 ? Double.POSITIVE_INFINITY : 1.0 / (1.0 + addSum);
            double remaining = baseSum == Double.POSITIVE_INFINITY ? 0.0 : multiply(Math.max(0.0, afterAdds - baseSum), totalProduct);
            yield remaining == 0.0 ? cap : clampMagnitude(1.0 / remaining - 1.0, cap);
         }
      };
   }

   private static double multiply(double a, double b){
      if(a == 0.0 || b == 0.0) return 0.0;
      return a * b;
   }

   private static float clampMagnitude(double v, float cap){
      if(!(v > 0.0)) return 0.0f;
      return v >= cap ? cap : (float) v;
   }
}
