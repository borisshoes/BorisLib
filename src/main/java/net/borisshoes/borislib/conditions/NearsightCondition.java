package net.borisshoes.borislib.conditions;

import eu.pb4.factorytools.api.virtualentity.ItemDisplayElementUtil;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import net.borisshoes.borislib.BorisLib;
import net.borisshoes.borislib.tracker.PlayerMovementEntry;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static net.borisshoes.borislib.BorisLib.MOD_ID;

/**
 * Built-in harmful {@link Condition} that shrinks how far the affected entity can see.
 *
 * <p>The magnitude is an intensity; the sight radius is derived from it with
 * {@link #radiusForMagnitude(float)}: {@code REFERENCE_RADIUS / (1 + m)}, floored at
 * {@link #MIN_RADIUS}. Intensity 1 gives 128 blocks, 3 gives 64, 7 gives 32, 15 gives 16 and {@code +∞} gives
 * {@link #MIN_RADIUS}, so more stacks mean less sight.</p>
 *
 * <p>For players the radius is rendered as nested boxes of virtual item displays around the camera, which
 * interpolate in place whenever the radius changes. For
 * other living entities the {@code canAttack} hook refuses targets further away than half the radius.
 * Entities tagged {@link BorisLib#IGNORES_NEARSIGHT} are immune.</p>
 *
 * <p>Cap: {@code +∞}.</p>
 */
public class NearsightCondition extends Condition {
   /** The largest sight radius, in blocks: the radius an intensity approaching 0 corresponds to. */
   public static final float REFERENCE_RADIUS = 256.0f;
   /** The smallest sight radius, in blocks, reached at infinite intensity. */
   public static final float MIN_RADIUS = 1.0f;

   private static final ConditionParticles PARTICLES = ConditionParticles.of(ParticleTypes.LARGE_SMOKE, 2.0f, 8, 0.04f);
   private static final Map<UUID, NearsightElementHolder> ACTIVE_HOLDERS = new ConcurrentHashMap<>();
   private static final Identifier SOLID_ID = Identifier.fromNamespaceAndPath(MOD_ID, "nearsight_solid");
   private static final Identifier TRANSPARENT_ID = Identifier.fromNamespaceAndPath(MOD_ID, "nearsight_transparent");
   private static final int UPDATE_INTERVAL = 5; // Ticks between overlay updates, and the length of each interpolation
   private static final float PLANE_THICKNESS = 0.01f;

   protected NearsightCondition(){
      super(Identifier.fromNamespaceAndPath(MOD_ID, "nearsight"), MobEffectCategory.HARMFUL, Float.POSITIVE_INFINITY);
   }

   /**
    * @param m the Nearsight intensity
    * @return the sight radius in blocks that intensity produces
    */
   public static float radiusForMagnitude(float m){
      if(Float.isInfinite(m)) return MIN_RADIUS;
      return Math.max(MIN_RADIUS, REFERENCE_RADIUS / (1.0f + m));
   }

   /**
    * @param r the desired sight radius in blocks
    * @return the Nearsight intensity that produces that radius; {@code 0} at or beyond
    *         {@link #REFERENCE_RADIUS} and {@code +∞} at or below {@link #MIN_RADIUS}
    */
   public static float magnitudeForRadius(float r){
      if(r >= REFERENCE_RADIUS) return 0.0f;
      if(r <= MIN_RADIUS) return Float.POSITIVE_INFINITY;
      return REFERENCE_RADIUS / r - 1.0f;
   }

   @Override
   public boolean canApply(LivingEntity entity){
      return !entity.is(BorisLib.IGNORES_NEARSIGHT);
   }

   @Override
   public ConditionMath.Scale getScale(){
      return ConditionMath.Scale.ATTENUATE;
   }

   @Override
   public ConditionParticles getDefaultParticles(){
      return PARTICLES;
   }

   @Override
   public void onTick(MinecraftServer server, LivingEntity entity, float value){
      if(entity instanceof ServerPlayer player && entity.tickCount % UPDATE_INTERVAL == 0){
         updateOverlay(player, radiusForMagnitude(value));
      }
   }

   @Override
   public void onDeactivate(MinecraftServer server, LivingEntity entity, float lastValue){
      removeHolder(entity.getUUID());
   }

   private void updateOverlay(ServerPlayer player, float radius){
      double[] boxThicknesses = calculateBoxes(radius);
      int normalBoxCount = boxThicknesses.length;
      double cameraCoverDistance = player.getAttributeValue(Attributes.CAMERA_DISTANCE) + 2.0;
      int extraBoxCount = calculateExtraBoxCount(radius, cameraCoverDistance);
      int boxCount = normalBoxCount + extraBoxCount;
      int requiredElements = boxCount * 6; // 6 directions per layer
      float viewRange = (float) Math.max(10.0, cameraCoverDistance + 2.0);

      final double eyeY = player.getEyeHeight(player.getPose());
      final boolean hasPack = PolymerResourcePackUtils.hasMainPack(player);
      final float scaleFactor = hasPack ? 1.0f : 2.0f;

      NearsightElementHolder holder = ACTIVE_HOLDERS.get(player.getUUID());

      // Rebuild only when there is no holder, the player reference is stale (reconnect), or the player changed dimension,
      // so Polymer respawns the virtual entities in the new world. Everything else is updated in place.
      boolean build = holder == null || holder.getPlayer() != player || !holder.getLevelKey().equals(player.level().dimension());
      if(build){
         if(holder != null){
            holder.setAttachment(null);
            holder.destroy();
         }
         holder = new NearsightElementHolder(player);
         ACTIVE_HOLDERS.put(player.getUUID(), holder);
      }
      holder.resetLifetime();

      // The number of extra boxes follows the radius. Surplus layers are dropped and missing ones are spawned without
      // touching the rest, so a changing radius never respawns the overlay.
      while(holder.getElements().size() > requiredElements){
         holder.removeElement(holder.getElements().get(holder.getElements().size() - 1));
      }
      boolean updateStacks = build || holder.hasPack != hasPack;
      ItemStack solidStack = null;
      ItemStack transparentStack = null;
      if(updateStacks || holder.getElements().size() < requiredElements){
         if(hasPack){
            solidStack = new ItemStack(Items.TRIAL_KEY);
            solidStack.set(DataComponents.ITEM_MODEL, SOLID_ID);
            transparentStack = new ItemStack(Items.TRIAL_KEY);
            transparentStack.set(DataComponents.ITEM_MODEL, TRANSPARENT_ID);
         }else{
            solidStack = new ItemStack(Items.CONCRETE.black());
            transparentStack = new ItemStack(Items.TINTED_GLASS);
         }
      }

      PlayerMovementEntry tracker = BorisLib.PLAYER_MOVEMENT_TRACKER.get(player);
      Vector3f velocity = tracker == null ? new Vector3f() : tracker.velocity().toVector3f();
      final Vec3 offset = new Vec3(0, eyeY, 0);
      final boolean updateOffset = build || holder.eyeY != eyeY;
      final boolean updateViewRange = build || holder.cameraCoverDistance != cameraCoverDistance;
      final double distStartFraction = 0.5; // On build, planes start at 50% of their distance and drift outward

      int elementIdx = 0;
      for(int i = 0; i < boxCount; i++){
         final boolean isExtraBox = i >= normalBoxCount;
         final double dist = isExtraBox ? calculateExtraBoxDistance(radius, cameraCoverDistance, i - normalBoxCount, extraBoxCount) : radius - boxThicknesses[i];
         final boolean isSolid = isExtraBox || i == normalBoxCount - 1;

         for(Direction dir : Direction.values()){
            Vector3f scale = planeScale(dir, dist).mul(scaleFactor);
            ItemDisplayElement element;
            if(elementIdx < holder.getElements().size()){
               element = (ItemDisplayElement) holder.getElements().get(elementIdx);
               if(updateStacks) element.setItem(isSolid ? solidStack : transparentStack);
               if(updateViewRange) element.setViewRange(viewRange);
               if(updateOffset) element.setOffset(offset);
            }else{
               // A new plane spawns already in place; only the first build animates outward
               element = ItemDisplayElementUtil.createSimple();
               element.setViewRange(viewRange);
               element.setDisplaySize(1024, 1024);
               element.setItem(isSolid ? solidStack : transparentStack);
               element.setOffset(offset);
               element.setScale(scale);
               element.setTranslation(planeTranslation(dir, build ? dist * distStartFraction : dist).add(velocity));
               element.setStartInterpolation(0);
               element.setInterpolationDuration(UPDATE_INTERVAL);
               holder.addElement(element);
            }
            elementIdx++;
         }
      }

      if(build){
         // Spawns every plane in its starting state; the targets written below then animate from there
         EntityAttachment attachment = new EntityAttachment(holder, player, true);
         attachment.startWatching(player);
      }

      // The distance of each plane lives in its translation, not its position, so it interpolates in lockstep with the
      // scale: the faces of a box stay joined at the seams while the radius changes. The player's velocity is added to
      // lead the display entities' position updates. One interpolation is started per update, lasting until the next.
      elementIdx = 0;
      for(int i = 0; i < boxCount; i++){
         final boolean isExtraBox = i >= normalBoxCount;
         final double dist = isExtraBox ? calculateExtraBoxDistance(radius, cameraCoverDistance, i - normalBoxCount, extraBoxCount) : radius - boxThicknesses[i];
         for(Direction dir : Direction.values()){
            ItemDisplayElement element = (ItemDisplayElement) holder.getElements().get(elementIdx++);
            element.setScale(planeScale(dir, dist).mul(scaleFactor));
            element.setTranslation(planeTranslation(dir, dist).add(velocity));
            element.startInterpolationIfDirty();
         }
      }

      holder.hasPack = hasPack;
      holder.eyeY = eyeY;
      holder.cameraCoverDistance = cameraCoverDistance;
   }

   private static Vector3f planeScale(Direction dir, double dist){
      float size = (float) (2 * dist);
      return switch(dir.getAxis()){
         case X -> new Vector3f(PLANE_THICKNESS, size, size);
         case Y -> new Vector3f(size, PLANE_THICKNESS, size);
         case Z -> new Vector3f(size, size, PLANE_THICKNESS);
      };
   }

   private static Vector3f planeTranslation(Direction dir, double dist){
      float along = (float) (dir.getAxisDirection() == Direction.AxisDirection.POSITIVE ? dist : -dist) - PLANE_THICKNESS / 2;
      return switch(dir.getAxis()){
         case X -> new Vector3f(along, 0, 0);
         case Y -> new Vector3f(0, along, 0);
         case Z -> new Vector3f(0, 0, along);
      };
   }

   /**
    * Destroys the overlay shown to a player, if any.
    *
    * @param playerId the player's UUID
    */
   public static void removeHolder(UUID playerId){
      NearsightElementHolder holder = ACTIVE_HOLDERS.remove(playerId);
      if(holder != null){
         holder.setAttachment(null);
         holder.destroy();
      }
   }

   private double[] calculateBoxes(float radius){
      int n = 8;
      double[] boxes = new double[n];
      for(int i = 1; i <= n; i++){
         double thickness = (2.0 * radius / 3.0 - 0.01) / (1 - n + 0.01) * (i - n) + 0.01;
         boxes[i - 1] = thickness;
      }
      return boxes;
   }

   private int calculateExtraBoxCount(float radius, double cameraCoverDistance){
      if(radius >= cameraCoverDistance){
         return 0;
      }
      return Math.max(1, (int) Math.ceil(cameraCoverDistance - radius));
   }

   private double calculateExtraBoxDistance(float radius, double cameraCoverDistance, int extraIndex, int extraBoxCount){
      return radius + (cameraCoverDistance - radius) * (extraIndex + 1) / extraBoxCount;
   }

   private static class NearsightElementHolder extends ElementHolder {
      private final ServerPlayer player;
      private final ResourceKey<Level> levelKey;
      private int lifeTime;
      // The state the elements were last written for, so unchanged updates send nothing
      private double eyeY;
      private double cameraCoverDistance;
      private boolean hasPack;

      private NearsightElementHolder(ServerPlayer player){
         this.player = player;
         this.levelKey = player.level().dimension();
         this.lifeTime = 10;
      }

      void resetLifetime(){
         this.lifeTime = 10;
      }

      public ServerPlayer getPlayer(){
         return player;
      }

      public ResourceKey<Level> getLevelKey(){
         return levelKey;
      }

      @Override
      public boolean startWatching(ServerGamePacketListenerImpl connection){
         if(connection.player != this.player){
            return false;
         }
         return super.startWatching(connection);
      }

      @Override
      protected void onTick(){
         super.onTick();

         if(lifeTime-- <= 0 || player.isDeadOrDying() || player.hasDisconnected()){
            ACTIVE_HOLDERS.remove(player.getUUID(), this);
            setAttachment(null);
            destroy();
         }
      }
   }
}
