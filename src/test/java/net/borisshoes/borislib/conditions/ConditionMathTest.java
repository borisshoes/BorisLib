package net.borisshoes.borislib.conditions;

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

   private static float combine(float cap, Term... terms){
      return ConditionMath.combine(List.of(terms), cap).value();
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
      PrevailingCondition none = ConditionMath.combine(List.<Term>of(), INF);
      assertEquals(0.0f, none.value());
      assertFalse(none.isActive());
   }

   @Test
   void nonStackingCompetesWithStacked(){
      PrevailingCondition nonStackingWins = ConditionMath.combine(List.of(add(0.2f), solo(0.3f), solo(0.1f)), INF);
      assertEquals(0.3f, nonStackingWins.value());
      assertFalse(nonStackingWins.fromStacking());

      PrevailingCondition tie = ConditionMath.combine(List.of(add(0.3f), solo(0.3f)), INF);
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
               assertFalse(Float.isNaN(combine(INF, add(a), base(b), total(c), total(a), solo(b))), a + " " + b + " " + c);
            }
         }
      }
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
