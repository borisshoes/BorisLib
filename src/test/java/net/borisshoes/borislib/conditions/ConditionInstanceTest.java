package net.borisshoes.borislib.conditions;

import com.mojang.serialization.Codec;
import net.minecraft.core.Holder;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConditionInstanceTest {
   private static final float INF = Float.POSITIVE_INFINITY;
   private static final Identifier ID = Identifier.fromNamespaceAndPath("test", "source");

   private static Holder<Condition> condition(float cap){
      return condition(cap, ConditionMath.Scale.LINEAR);
   }

   private static Holder<Condition> condition(float cap, ConditionMath.Scale scale){
      return Holder.direct(new Condition(Identifier.fromNamespaceAndPath("test", "condition"), MobEffectCategory.NEUTRAL, cap) {
         @Override
         public void onTick(MinecraftServer server, LivingEntity entity, float value){
         }

         @Override
         public ConditionMath.Scale getScale(){
            return scale;
         }
      });
   }

   @Test
   void rejectsInvalidValues(){
      Holder<Condition> holder = condition(INF);
      assertThrows(IllegalArgumentException.class, () -> new ConditionInstance(holder, ID, 20, Float.NaN));
      assertThrows(IllegalArgumentException.class, () -> new ConditionInstance(holder, ID, 20, Float.NEGATIVE_INFINITY));
      assertDoesNotThrow(() -> new ConditionInstance(holder, ID, 20, INF));
      assertDoesNotThrow(() -> new ConditionInstance(holder, ID, 20, -5.0f));
   }

   @Test
   void rejectsInvalidDurations(){
      Holder<Condition> holder = condition(INF);
      assertThrows(IllegalArgumentException.class, () -> new ConditionInstance(holder, ID, 0, 1.0f));
      assertThrows(IllegalArgumentException.class, () -> new ConditionInstance(holder, ID, -2, 1.0f));
      assertDoesNotThrow(() -> new ConditionInstance(holder, ID, ConditionInstance.INFINITE_DURATION, 1.0f));
   }

   @Test
   void rejectsNonStackingMultipliers(){
      Holder<Condition> holder = condition(INF);
      assertThrows(IllegalArgumentException.class, () -> new ConditionInstance(holder, ID, 20, 1.0f, false, true, Operation.ADD_MULTIPLIED_BASE));
      assertThrows(IllegalArgumentException.class, () -> new ConditionInstance(holder, ID, 20, 1.0f, false, true, Operation.ADD_MULTIPLIED_TOTAL));
      assertDoesNotThrow(() -> new ConditionInstance(holder, ID, 20, 1.0f, false, true, Operation.ADD_VALUE));

      // On a factor scale a lone percentage is meaningful, so it is allowed
      Holder<Condition> attenuating = condition(INF, ConditionMath.Scale.ATTENUATE);
      assertDoesNotThrow(() -> new ConditionInstance(attenuating, ID, 20, 0.2f, false, true, Operation.ADD_MULTIPLIED_BASE));
   }

   @Test
   void percentInstancesStackOnTheConditionScale(){
      Holder<Condition> resist = condition(INF, ConditionMath.Scale.ATTENUATE);
      List<ConditionInstance> instances = List.of(
            new ConditionInstance(resist, Identifier.fromNamespaceAndPath("test", "armor"), 20, 0.15f, Operation.ADD_MULTIPLIED_BASE),
            new ConditionInstance(resist, Identifier.fromNamespaceAndPath("test", "potion"), 20, 0.20f, Operation.ADD_MULTIPLIED_BASE));
      assertEquals(0.35f, ConditionMath.magnitudeToReduction(ConditionMath.combine(instances, resist.value()).value()), 1e-6f);
   }

   @Test
   void timerExpiresUnlessInfinite(){
      Holder<Condition> holder = condition(INF);
      ConditionInstance timed = new ConditionInstance(holder, ID, 2, 1.0f);
      assertFalse(timed.tick());
      assertTrue(timed.tick());

      ConditionInstance endless = new ConditionInstance(holder, ID, ConditionInstance.INFINITE_DURATION, 1.0f);
      for(int i = 0; i < 1000; i++){
         assertFalse(endless.tick());
      }
   }

   @Test
   void combineUsesTheConditionCap(){
      Holder<Condition> finite = condition(Float.MAX_VALUE);
      assertFalse(finite.value().supportsInfinity());
      assertEquals(Float.MAX_VALUE, ConditionMath.combine(List.of(new ConditionInstance(finite, ID, 20, INF)), finite.value()).value());

      Holder<Condition> infinite = condition(INF);
      assertTrue(infinite.value().supportsInfinity());
      assertEquals(INF, ConditionMath.combine(List.of(new ConditionInstance(infinite, ID, 20, INF)), infinite.value()).value());
   }

   @Test
   void conditionRejectsInvalidCaps(){
      assertThrows(IllegalArgumentException.class, () -> condition(0.0f));
      assertThrows(IllegalArgumentException.class, () -> condition(-1.0f));
      assertThrows(IllegalArgumentException.class, () -> condition(Float.NaN));
   }

   @Test
   void sanitizeClampsToCap(){
      Condition capped = condition(10.0f).value();
      assertEquals(0.0f, capped.sanitizeValue(Float.NaN));
      assertEquals(0.0f, capped.sanitizeValue(-3.0f));
      assertEquals(10.0f, capped.sanitizeValue(INF));
      assertEquals(4.0f, capped.sanitizeValue(4.0f));
   }

   @Test
   void infinitySurvivesNbtRoundTrip(){
      Tag encoded = Codec.FLOAT.encodeStart(NbtOps.INSTANCE, INF).getOrThrow();
      assertEquals(INF, Codec.FLOAT.parse(NbtOps.INSTANCE, encoded).getOrThrow());
   }
}
