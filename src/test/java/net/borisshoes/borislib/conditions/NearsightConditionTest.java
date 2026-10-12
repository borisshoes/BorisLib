package net.borisshoes.borislib.conditions;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NearsightConditionTest {
   private static final float INF = Float.POSITIVE_INFINITY;

   @BeforeAll
   static void bootstrap(){
      // NearsightCondition's particle spec references the particle registry
      SharedConstants.tryDetectVersion();
      Bootstrap.bootStrap();
   }

   @Test
   void radiusShrinksWithIntensity(){
      assertEquals(32.0f, NearsightCondition.radiusForMagnitude(0.0f));
      assertEquals(16.0f, NearsightCondition.radiusForMagnitude(1.0f));
      assertEquals(10.67f, NearsightCondition.radiusForMagnitude(2.0f), 0.01f);
      assertEquals(8.0f, NearsightCondition.radiusForMagnitude(3.0f));
      assertEquals(2.0f, NearsightCondition.radiusForMagnitude(15.0f));
      assertEquals(NearsightCondition.MIN_RADIUS, NearsightCondition.radiusForMagnitude(1000.0f));
      assertEquals(NearsightCondition.MIN_RADIUS, NearsightCondition.radiusForMagnitude(INF));
   }

   @Test
   void magnitudeIsTheInverseOfRadius(){
      assertEquals(0.0f, NearsightCondition.magnitudeForRadius(32.0f));
      assertEquals(0.0f, NearsightCondition.magnitudeForRadius(64.0f));
      assertEquals(1.0f, NearsightCondition.magnitudeForRadius(16.0f));
      assertEquals(7.0f, NearsightCondition.magnitudeForRadius(4.0f));
      assertEquals(INF, NearsightCondition.magnitudeForRadius(NearsightCondition.MIN_RADIUS));
      assertEquals(INF, NearsightCondition.magnitudeForRadius(0.5f));
   }
}
