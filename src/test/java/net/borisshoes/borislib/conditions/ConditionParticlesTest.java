package net.borisshoes.borislib.conditions;

import net.minecraft.SharedConstants;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConditionParticlesTest {

   @BeforeAll
   static void bootstrap(){
      // The particle codec references the particle registry
      SharedConstants.tryDetectVersion();
      Bootstrap.bootStrap();
   }

   @Test
   void roundTripsValidSpec(){
      ConditionParticles spec = ConditionParticles.of(ParticleTypes.HEART, 2.0f, 5, 0.1f);
      CompoundTag tag = (CompoundTag) ConditionParticles.CODEC.encodeStart(NbtOps.INSTANCE, spec).getOrThrow();
      assertEquals(spec, ConditionParticles.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow());
   }

   @Test
   void invalidSavedSpecDecodesToErrorInsteadOfThrowing(){
      CompoundTag tag = (CompoundTag) ConditionParticles.CODEC.encodeStart(NbtOps.INSTANCE, ConditionParticles.of(ParticleTypes.HEART, 2.0f, 5, 0.1f)).getOrThrow();
      tag.putInt("burst", -5);
      var result = assertDoesNotThrow(() -> ConditionParticles.CODEC.parse(NbtOps.INSTANCE, tag));
      assertTrue(result.isError());
   }
}
