package net.borisshoes.borislib.conditions;

import eu.pb4.factorytools.api.virtualentity.ItemDisplayElementUtil;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import net.borisshoes.borislib.BorisLib;
import net.borisshoes.borislib.tracker.PlayerMovementEntry;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static net.borisshoes.borislib.BorisLib.MOD_ID;

public class NearsightCondition extends Condition {
   private static final SimpleParticleType PARTICLE = ParticleTypes.LARGE_SMOKE;
   private static final Map<UUID, NearsightElementHolder> ACTIVE_HOLDERS = new ConcurrentHashMap<>();
   private static final Identifier SOLID_ID = Identifier.fromNamespaceAndPath(MOD_ID, "nearsight_solid");
   private static final Identifier TRANSPARENT_ID = Identifier.fromNamespaceAndPath(MOD_ID, "nearsight_transparent");
   
   protected NearsightCondition(){
      super(Identifier.fromNamespaceAndPath(MOD_ID, "nearsight"), MobEffectCategory.HARMFUL, 0.0f, -128.0f, 0.0f);
   }
   
   @Override
   public void onTick(MinecraftServer server, LivingEntity entity, float value, boolean particles){
      value = -value;
      if(entity.level() instanceof ServerLevel level){
         if(particles && level.random.nextFloat() < 0.1){
            double width = entity.getBbWidth();
            double height = entity.getBbHeight();
            level.sendParticles(PARTICLE, entity.getX() + (entity.getRandom().nextDouble() - 0.5) * width, entity.getY() + entity.getRandom().nextDouble() * height, entity.getZ() + (entity.getRandom().nextDouble() - 0.5) * width, 1, 0.1, 0.15, 0.1, 0.04);
         }
         
         if(entity instanceof ServerPlayer player && server != null && server.getTickCount() % 5 == 0){
            double[] boxThicknesses = calculateBoxes(value);
            int boxCount = boxThicknesses.length;
            int requiredElements = boxCount * 6; // 6 directions per layer
            
            final double eyeY = entity.getEyeHeight(entity.getPose());
            final double thinThickness = 0.01;
            
            NearsightElementHolder holder = ACTIVE_HOLDERS.get(player.getUUID());
            
            // If no holder exists, the element count changed, the value changed, or the player reference is stale (reconnect), rebuild from scratch
            if(holder == null || holder.getElements().size() != requiredElements || holder.getValue() != value || holder.getPlayer() != player){
               if(holder != null){
                  holder.setAttachment(null);
                  holder.destroy();
               }
               
               holder = new NearsightElementHolder(player, value);
               for(int i = 0; i < requiredElements; i++){
                  ItemDisplayElement element = ItemDisplayElementUtil.createSimple();
                  element.setViewRange(10.0f);
                  element.setDisplaySize(1024, 1024);
                  holder.addElement(element);
               }
               
               EntityAttachment attachment = new EntityAttachment(holder, player, true);
               attachment.startWatching(player);
               
               for(ServerPlayer serverPlayer : server.getPlayerList().getPlayers()){
                  if(serverPlayer != player){
                     holder.stopWatching(serverPlayer);
                     attachment.stopWatching(serverPlayer);
                  }
               }
               
               ACTIVE_HOLDERS.put(player.getUUID(), holder);
            }
            
            // Update existing elements in place
            holder.resetLifetime();
            boolean animateIn = holder.isNew();
            final int animDuration = 5; // Ticks for the expand animation
            final double distStartFraction = 0.5; // Distance starts at 80% of final (slow outward drift)
            final double scaleStartFraction = 1.00; // Scale starts at 5% of final (fast expansion)
            
            int elementIdx = 0;
            for(int i = 0; i < boxCount; i++){
               final double dist = value - boxThicknesses[i];
               final double layerSize = 2 * dist;
               final boolean isOutermost = (i == boxCount - 1);
               
               for(Direction dir : Direction.values()){
                  ItemDisplayElement element = (ItemDisplayElement) holder.getElements().get(elementIdx++);
                  if(PolymerResourcePackUtils.hasMainPack(player)){
                     ItemStack stack = new ItemStack(Items.TRIAL_KEY);
                     stack.set(DataComponents.ITEM_MODEL, isOutermost ? SOLID_ID : TRANSPARENT_ID);
                     element.setItem(stack);
                  }else{
                     element.setItem(isOutermost ? new ItemStack(Items.BLACK_CONCRETE) : new ItemStack(Items.TINTED_GLASS));
                  }
                  
                  if(animateIn){
                     // Set initial state: small planes close to their final position
                     double startDist = dist * distStartFraction;
                     double startLayerSize = layerSize * scaleStartFraction;
                     
                     Vec3 startScale = switch(dir.getAxis()){
                        case X -> new Vec3(thinThickness, startLayerSize, startLayerSize);
                        case Y -> new Vec3(startLayerSize, thinThickness, startLayerSize);
                        case Z -> new Vec3(startLayerSize, startLayerSize, thinThickness);
                     };
                     element.setScale(startScale.toVector3f());
                     
                     double startAxisOffset = dir.getAxisDirection() == Direction.AxisDirection.POSITIVE ? startDist - thinThickness / 2 : -startDist - thinThickness / 2;
                     Vec3 startOffset = switch(dir.getAxis()){
                        case X -> new Vec3(startAxisOffset, eyeY, 0);
                        case Y -> new Vec3(0, startAxisOffset + eyeY, 0);
                        case Z -> new Vec3(0, eyeY, startAxisOffset);
                     };
                     element.setOffset(startOffset);
                     
                     // Send the initial small state to the client
                     element.setInterpolationDuration(0);
                     element.tick();
                     
                     // Now set the final state with interpolation so it animates outward
                     element.setStartInterpolation(0);
                     element.setInterpolationDuration(animDuration);
                  }
                  
                  // Set the final scale and position
                  Vec3 scale = switch(dir.getAxis()){
                     case X -> new Vec3(thinThickness, layerSize, layerSize);
                     case Y -> new Vec3(layerSize, thinThickness, layerSize);
                     case Z -> new Vec3(layerSize, layerSize, thinThickness);
                  };
                  element.setScale(scale.toVector3f());
                  
                  double axisOffset = dir.getAxisDirection() == Direction.AxisDirection.POSITIVE ? dist - thinThickness / 2 : -dist - thinThickness / 2;
                  
                  Vec3 offset = switch(dir.getAxis()){
                     case X -> new Vec3(axisOffset, eyeY, 0);
                     case Y -> new Vec3(0, axisOffset + eyeY, 0);
                     case Z -> new Vec3(0, eyeY, axisOffset);
                  };
                  element.setOffset(offset);
               }
            }
            
            if(animateIn){
               holder.setNew(false);
            }
         }
      }
   }
   
   @Override
   public void onApply(MinecraftServer server, LivingEntity entity, float value, boolean particles){
      if(particles && entity.level() instanceof ServerLevel level){
         double width = entity.getBbWidth();
         double height = entity.getBbHeight();
         level.sendParticles(PARTICLE, entity.getX(), entity.getY() + height * 0.5, entity.getZ(), 8, width * 0.5, height * 0.4, width * 0.5, 0);
      }
   }
   
   @Override
   public void onRemove(MinecraftServer server, LivingEntity entity, float value, boolean particles){
      removeHolder(entity.getUUID());
   }
   
   public static void removeHolder(UUID playerId){
      NearsightElementHolder holder = ACTIVE_HOLDERS.remove(playerId);
      if(holder != null){
         holder.setAttachment(null);
         holder.destroy();
      }
   }
   
   private double[] calculateBoxes(float value){
      int n = 8;
      double[] boxes = new double[n];
      for(int i = 1; i <= n; i++){
         double thickness = (2.0 * value / 3.0 - 0.01) / (1 - n + 0.01) * (i - n) + 0.01;
         boxes[i - 1] = thickness;
      }
      return boxes;
   }
   
   private static class NearsightElementHolder extends ElementHolder {
      private final ServerPlayer player;
      private final float value;
      private int lifeTime;
      private boolean isNew;
      
      private NearsightElementHolder(ServerPlayer player, float value){
         this.player = player;
         this.lifeTime = 10;
         this.value = value;
         this.isNew = true;
      }
      
      void resetLifetime(){
         this.lifeTime = 10;
      }
      
      public boolean isNew(){
         return isNew;
      }
      
      public void setNew(boolean isNew){
         this.isNew = isNew;
      }
      
      public float getValue(){
         return value;
      }
      
      public ServerPlayer getPlayer(){
         return player;
      }
      
      @Override
      protected void onTick(){
         super.onTick();
         
         if(lifeTime % 5 == 0){
            PlayerMovementEntry tracker = BorisLib.PLAYER_MOVEMENT_TRACKER.get(player);
            Vec3 vel = tracker == null ? new Vec3(0, 0, 0) : tracker.velocity();
            for(VirtualElement e : getElements()){
               if(e instanceof ItemDisplayElement element){
                  element.setTranslation(vel.toVector3f().mul(1));
                  element.setStartInterpolation(-1);
                  element.setInterpolationDuration(5);
                  element.startInterpolation();
               }
            }
         }
         
         if(lifeTime-- <= 0 || player.isDeadOrDying() || player.hasDisconnected()){
            ACTIVE_HOLDERS.remove(player.getUUID());
            setAttachment(null);
            destroy();
         }
      }
   }
}
