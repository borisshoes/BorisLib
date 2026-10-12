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
 * <p>Equal magnitudes of an opposing pair cancel exactly. Nothing in this class touches the server, so it
 * can be unit tested without a running game.</p>
 */
public final class ConditionMath {

   private ConditionMath(){
   }

   /**
    * The minimal view of a {@link ConditionInstance} that {@link #combine(List, float)} needs. Exists so the
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
    * Combines every instance of one condition type into its prevailing magnitude.
    *
    * @param instances instances of one condition type
    * @param condition the condition type, which supplies the cap
    * @return the prevailing state; its value is {@code 0} when nothing is in effect
    * @see #combine(List, float)
    */
   public static PrevailingCondition combine(List<ConditionInstance> instances, Condition condition){
      return combine(instances, condition.getCap());
   }

   /**
    * Combines terms of one condition type into a prevailing magnitude, in a single pass.
    *
    * <p>Stacking terms are folded in vanilla attribute order:</p>
    * <ol>
    *    <li>{@code A} = the sum of all {@code ADD_VALUE} values.</li>
    *    <li>{@code B} = {@code A + A × S}, where {@code S} is the sum of {@code ADD_MULTIPLIED_BASE}
    *        values.</li>
    *    <li>{@code C} = {@code B × Π(1 + t)} over {@code ADD_MULTIPLIED_TOTAL} values {@code t}.</li>
    *    <li>{@code stacked = clamp(C, 0, cap)}.</li>
    * </ol>
    * <p>{@code 0 × ∞} is defined as {@code 0}, and infinity is absorbing: once {@code A} or {@code B} is
    * {@code +∞} no multiplier can cancel it. Each non-stacking term is a candidate
    * {@code clamp(v, 0, cap)}; the result is the larger of the stacked value and the best candidate, with
    * the stacked value winning ties.</p>
    *
    * @param terms terms of one condition type
    * @param cap   the largest permitted magnitude
    * @return the prevailing state; its value is {@code 0} when nothing is in effect
    */
   public static PrevailingCondition combine(List<? extends Term> terms, float cap){
      double addSum = 0.0;
      double baseSum = 0.0;
      double totalProduct = 1.0;
      float bestNonStacking = 0.0f;

      for(int i = 0; i < terms.size(); i++){
         Term term = terms.get(i);
         float v = term.getValue();
         if(!term.isStacking()){
            float candidate = clampMagnitude(v, cap);
            if(candidate > bestNonStacking) bestNonStacking = candidate;
            continue;
         }
         switch(term.getOperation()){
            case ADD_VALUE -> addSum += v;
            case ADD_MULTIPLIED_BASE -> baseSum += v;
            case ADD_MULTIPLIED_TOTAL -> totalProduct = multiply(totalProduct, 1.0 + v);
         }
      }

      double b = addSum == Double.POSITIVE_INFINITY ? addSum : addSum + multiply(addSum, baseSum);
      double c = b == Double.POSITIVE_INFINITY ? b : multiply(b, totalProduct);
      float stacked = clampMagnitude(c, cap);

      if(bestNonStacking > stacked) return new PrevailingCondition(bestNonStacking, false);
      return new PrevailingCondition(stacked, true);
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
