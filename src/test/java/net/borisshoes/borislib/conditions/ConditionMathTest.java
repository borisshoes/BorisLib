package net.borisshoes.borislib.conditions;

import net.borisshoes.borislib.conditions.ConditionMath.Scale;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConditionMathTest {
   private static final float INF = Float.POSITIVE_INFINITY;

   private record Term(float getValue, Operation getOperation, boolean isStacking) implements ConditionMath.Term {
   }

   private static Term add(float value){
      return new Term(value, Operation.ADD_VALUE, true);
   }

   private static Term base(float value){
      return new Term(value, Operation.ADD_MULTIPLIED_BASE, true);
   }

   private static Term total(float value){
      return new Term(value, Operation.ADD_MULTIPLIED_TOTAL, true);
   }

   private static Term solo(float value){
      return new Term(value, Operation.ADD_VALUE, false);
   }

   private static Term solo(float value, Operation operation){
      return new Term(value, operation, false);
   }

   private static float combine(float cap, Term... terms){
      return ConditionMath.combine(List.of(terms), cap, Scale.LINEAR).value();
   }

   private static float amplify(float cap, Term... terms){
      return ConditionMath.combine(List.of(terms), cap, Scale.AMPLIFY).value();
   }

   private static float attenuate(float cap, Term... terms){
      return ConditionMath.combine(List.of(terms), cap, Scale.ATTENUATE).value();
   }

   /** The fraction of the effect an attenuating magnitude removes. */
   private static float reduction(float magnitude){
      return ConditionMath.magnitudeToReduction(magnitude);
   }

   @Test
   void stackingAddsSum(){
      assertEquals(1.0f, combine(INF, add(0.5f), add(0.5f)));
   }

   @Test
   void multipliersFollowAttributeOrder(){
      assertEquals(3.0f, combine(INF, add(2.0f), base(0.5f)));
      assertEquals(3.0f, combine(INF, add(2.0f), total(0.5f)));
      assertEquals(9.0f, combine(INF, add(2.0f), base(0.5f), base(0.5f), total(0.5f), total(0.5f)));
   }

   @Test
   void infinityIsAbsorbing(){
      assertEquals(INF, combine(INF, add(INF), total(-1.0f)));
      assertEquals(INF, combine(INF, add(INF), add(-5.0f), base(-1.0f)));
   }

   @Test
   void zeroTimesInfinityIsZero(){
      assertEquals(0.0f, combine(INF, base(INF)));
      assertEquals(0.0f, combine(INF, add(1.0f), total(-1.0f), total(INF)));
   }

   @Test
   void negativeResultsClampToZero(){
      assertEquals(0.0f, combine(INF, add(-5.0f)));
      assertEquals(0.0f, combine(INF, add(-1.0f), base(INF)));
      assertEquals(0.0f, combine(INF, solo(-3.0f)));
   }

   @Test
   void nothingInEffectIsZero(){
      PrevailingCondition none = ConditionMath.combine(List.<Term>of(), INF, Scale.LINEAR);
      assertEquals(0.0f, none.value());
      assertFalse(none.isActive());
   }

   @Test
   void nonStackingCompetesWithStacked(){
      PrevailingCondition nonStackingWins = ConditionMath.combine(List.of(add(0.2f), solo(0.3f), solo(0.1f)), INF, Scale.LINEAR);
      assertEquals(0.3f, nonStackingWins.value());
      assertFalse(nonStackingWins.fromStacking());

      PrevailingCondition tie = ConditionMath.combine(List.of(add(0.3f), solo(0.3f)), INF, Scale.LINEAR);
      assertEquals(0.3f, tie.value());
      assertTrue(tie.fromStacking());
   }

   @Test
   void infinityClampsToFiniteCap(){
      assertEquals(Float.MAX_VALUE, combine(Float.MAX_VALUE, add(INF)));
      assertEquals(Float.MAX_VALUE, combine(Float.MAX_VALUE, solo(INF)));
      assertEquals(5.0f, combine(5.0f, add(4.0f), add(4.0f)));
   }

   @Test
   void neverProducesNaN(){
      float[] values = {INF, -Float.MAX_VALUE, -1.0f, 0.0f, 1.0f, Float.MAX_VALUE};
      for(float a : values){
         for(float b : values){
            for(float c : values){
               for(Scale scale : Scale.values()){
                  List<Term> terms = List.of(add(a), base(b), total(c), total(a), solo(b), solo(c, Operation.ADD_MULTIPLIED_BASE), solo(a, Operation.ADD_MULTIPLIED_TOTAL));
                  float value = ConditionMath.combine(terms, INF, scale).value();
                  assertFalse(Float.isNaN(value) || value < 0.0f, scale + " " + a + " " + b + " " + c);
               }
            }
         }
      }
   }

   @Test
   void addsAreMagnitudesOnEveryScale(){
      for(Scale scale : Scale.values()){
         assertEquals(0.3f, ConditionMath.combine(List.of(add(0.1f), add(0.2f)), INF, scale).value());
         assertEquals(0.0f, ConditionMath.combine(List.of(add(0.5f), add(-0.5f)), INF, scale).value());
         assertEquals(INF, ConditionMath.combine(List.of(add(INF), base(-1.0f), total(-1.0f)), INF, scale).value());
      }
   }

   @Test
   void attenuatePercentagesAddUp(){
      // 15% + 20% resist = 35% resist
      assertEquals(0.35f, reduction(attenuate(INF, base(0.15f), base(0.20f))), 1e-6f);
      // Magnitudes alone give diminishing returns instead
      assertEquals(0.29897f, reduction(attenuate(INF, add(ConditionMath.reductionToMagnitude(0.15f)), add(ConditionMath.reductionToMagnitude(0.20f)))), 1e-4f);
   }

   @Test
   void attenuatePercentagesCompound(){
      // 0.85 x 0.80 = 0.68 of the effect remains
      assertEquals(0.32f, reduction(attenuate(INF, total(0.15f), total(0.20f))), 1e-6f);
      // Base percentages add first, then total percentages compound on what remains: 0.65 x 0.5
      assertEquals(0.675f, reduction(attenuate(INF, base(0.15f), base(0.20f), total(0.5f))), 1e-6f);
      // A magnitude of 1 halves the effect, and a base 20% takes another 20 points off
      assertEquals(0.7f, reduction(attenuate(INF, add(1.0f), base(0.2f))), 1e-6f);
      assertEquals(INF, attenuate(INF, add(1.0f), base(0.5f)));
   }

   @Test
   void attenuateSaturatesAtTheCap(){
      assertEquals(INF, attenuate(INF, base(0.6f), base(0.4f)));
      assertEquals(INF, attenuate(INF, base(0.9f), base(0.9f), total(0.5f)));
      assertEquals(INF, attenuate(INF, total(1.0f), base(0.1f)));
      assertEquals(Float.MAX_VALUE, attenuate(Float.MAX_VALUE, base(1.0f)));
      // Over 100% never flips into a bonus, and a negative percentage only weakens the condition
      assertEquals(INF, attenuate(INF, total(1.5f), total(1.5f)));
      assertEquals(0.1f, reduction(attenuate(INF, base(0.3f), base(-0.2f))), 1e-6f);
      assertEquals(0.0f, attenuate(INF, base(-0.5f)));
   }

   @Test
   void amplifyPercentagesAddAndCompound(){
      // +15% and +20% add to +35%, the same as adding magnitudes
      assertEquals(0.35f, amplify(INF, base(0.15f), base(0.20f)), 1e-6f);
      assertEquals(0.35f, amplify(INF, add(0.15f), base(0.20f)), 1e-6f);
      // x1.15 x 1.20 = x1.38
      assertEquals(0.38f, amplify(INF, total(0.15f), total(0.20f)), 1e-6f);
      // (1 + 1 + 0.5) x 1.5 = 3.75
      assertEquals(2.75f, amplify(INF, add(1.0f), base(0.5f), total(0.5f)), 1e-6f);
      // A penalty can remove the bonus but never turns it into a debuff
      assertEquals(0.0f, amplify(INF, add(0.5f), total(-1.0f)));
      assertEquals(0.0f, amplify(INF, base(-2.0f), total(-2.0f)));
   }

   @Test
   void linearPercentagesNeedAMagnitude(){
      assertEquals(0.0f, combine(INF, base(0.5f), total(0.5f)));
      assertEquals(0.0f, combine(INF, solo(0.5f, Operation.ADD_MULTIPLIED_BASE)));
   }

   @Test
   void nonStackingPercentagesStandAlone(){
      PrevailingCondition aura = ConditionMath.combine(List.of(base(0.15f), solo(0.2f, Operation.ADD_MULTIPLIED_BASE), solo(0.1f, Operation.ADD_MULTIPLIED_TOTAL)), INF, Scale.ATTENUATE);
      assertEquals(0.2f, reduction(aura.value()), 1e-6f);
      assertFalse(aura.fromStacking());
      assertEquals(0.25f, amplify(INF, solo(0.25f, Operation.ADD_MULTIPLIED_TOTAL), solo(0.1f)), 1e-6f);
      assertEquals(INF, attenuate(INF, solo(1.0f, Operation.ADD_MULTIPLIED_BASE)));
   }

   @Test
   void curvesCancelAtEqualMagnitude(){
      assertEquals(2.0f, ConditionMath.amplify(1.0f));
      assertEquals(0.5f, ConditionMath.attenuate(1.0f));
      assertEquals(0.0f, ConditionMath.attenuate(INF));
      assertEquals(1.0f, ConditionMath.amplify(3.0f) * ConditionMath.attenuate(3.0f));
   }

   @Test
   void ratioLetsAttenuationWin(){
      assertEquals(0.0f, ConditionMath.ratio(INF, INF));
      assertEquals(INF, ConditionMath.ratio(INF, 1.0f));
      assertEquals(1.0f, ConditionMath.ratio(1.0f, 1.0f));
      assertEquals(0.0f, ConditionMath.ratio(100.0f, INF));
      assertEquals(0.0f, ConditionMath.multiplyFactors(0.0f, INF));
   }

   @Test
   void reductionConversions(){
      assertEquals(1.0f, ConditionMath.reductionToMagnitude(0.5f));
      assertEquals(3.0f, ConditionMath.reductionToMagnitude(0.75f));
      assertEquals(INF, ConditionMath.reductionToMagnitude(1.0f));
      assertThrows(IllegalArgumentException.class, () -> ConditionMath.reductionToMagnitude(1.2f));
      assertThrows(IllegalArgumentException.class, () -> ConditionMath.reductionToMagnitude(-0.1f));
      assertEquals(0.75f, ConditionMath.magnitudeToReduction(3.0f));
      assertEquals(1.0f, ConditionMath.magnitudeToReduction(INF));
   }

   @Test
   void amplifyConversions(){
      assertEquals(1.0f, ConditionMath.amplifyToMagnitude(2.0f));
      assertEquals(INF, ConditionMath.amplifyToMagnitude(INF));
      assertThrows(IllegalArgumentException.class, () -> ConditionMath.amplifyToMagnitude(0.5f));
   }

   @Test
   void levityConversions(){
      assertEquals(0.5f, ConditionMath.levityForGravityFactor(0.5f));
      assertEquals(1.0f, ConditionMath.levityForGravityFactor(0.0f));
      assertEquals(2.0f, ConditionMath.levityForGravityFactor(-1.0f));
      assertEquals(3.5f, ConditionMath.levityForGravityFactor(-2.5f));
      assertThrows(IllegalArgumentException.class, () -> ConditionMath.levityForGravityFactor(1.5f));
   }
}
