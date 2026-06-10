package net.borisshoes.borislib.sequences;

import net.borisshoes.borislib.datastorage.DataKey;
import net.borisshoes.borislib.datastorage.DataRegistry;
import net.borisshoes.borislib.datastorage.StorableData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.ValueInput;

import java.util.UUID;

/**
 * Persistent per-player snapshot of state captured immediately before a
 * {@link PlayerSequence} begins. Stored in BorisLib's player data store so
 * it survives server restarts and crashes.
 *
 * <p>When a sequence ends for any reason (natural completion, cancellation,
 * player disconnect, death, or server restart) the {@link SequenceManager}
 * reads this snapshot and fully restores the player to their pre-sequence state.
 */
public class PlayerSnapshot implements StorableData {
   
   /**
    * DataKey for player-scoped persistence. Registered on class load.
    */
   public static final DataKey<PlayerSnapshot> KEY = DataRegistry.register(DataKey.ofPlayer(Identifier.fromNamespaceAndPath("borislib", "sequence_snapshot"), PlayerSnapshot::new));
   
   private final UUID playerUUID;
   private Runnable dirtyCallback = () -> {
   };
   
   // ── Snapshot fields ────────────────────────────────────────────────────
   private boolean active = false;
   private String sequenceType = "";
   private int gameModeId = GameType.SURVIVAL.getId();
   private String dimension = "minecraft:overworld";
   private double x = 0.0, y = 64.0, z = 0.0;
   private float yaw = 0f, pitch = 0f;
   private boolean wasInvulnerable = false;
   private boolean wasAllowFlying = false;
   private boolean wasFlying = false;
   /**
    * UUID string of the camera Marker entity. Empty if no camera entity was spawned.
    */
   private String cameraEntityUUID = "";
   
   public PlayerSnapshot(UUID playerUUID){
      this.playerUUID = playerUUID;
   }
   
   // ── StorableData ──────────────────────────────────────────────────────
   
   @Override
   public void setDirtyCallback(Runnable callback){
      this.dirtyCallback = callback;
   }
   
   @Override
   public void markDirty(){
      dirtyCallback.run();
   }
   
   @Override
   public void read(ValueInput view){
      // Store all fields inside a single CompoundTag wrapped in a one-element list.
      // This uses only view.listOrEmpty() which is confirmed available, and reads
      // primitives directly from CompoundTag. In MC 26.1, CompoundTag getters return
      // Optional<T>, so we call .orElse() on every field.
      for(CompoundTag data : view.listOrEmpty("snap_v1", CompoundTag.CODEC)){
         active = data.getBoolean("active").orElse(false);
         sequenceType = data.getString("sequenceType").orElse("");
         gameModeId = data.getInt("gameModeId").orElse(GameType.SURVIVAL.getId());
         dimension = data.getString("dimension").orElse("minecraft:overworld");
         x = data.getDouble("x").orElse(0.0);
         y = data.getDouble("y").orElse(64.0);
         z = data.getDouble("z").orElse(0.0);
         yaw = data.getFloat("yaw").orElse(0f);
         pitch = data.getFloat("pitch").orElse(0f);
         wasInvulnerable = data.getBoolean("wasInvulnerable").orElse(false);
         wasAllowFlying = data.getBoolean("wasAllowFlying").orElse(false);
         wasFlying = data.getBoolean("wasFlying").orElse(false);
         cameraEntityUUID = data.getString("cameraEntityUUID").orElse("");
         break; // Only one entry is ever written
      }
   }
   
   @Override
   public void writeNbt(CompoundTag tag){
      CompoundTag data = new CompoundTag();
      data.putBoolean("active", active);
      data.putString("sequenceType", sequenceType);
      data.putInt("gameModeId", gameModeId);
      data.putString("dimension", dimension);
      data.putDouble("x", x);
      data.putDouble("y", y);
      data.putDouble("z", z);
      data.putFloat("yaw", yaw);
      data.putFloat("pitch", pitch);
      data.putBoolean("wasInvulnerable", wasInvulnerable);
      data.putBoolean("wasAllowFlying", wasAllowFlying);
      data.putBoolean("wasFlying", wasFlying);
      data.putString("cameraEntityUUID", cameraEntityUUID != null ? cameraEntityUUID : "");
      ListTag list = new ListTag();
      list.add(data);
      tag.put("snap_v1", list);
   }
   
   // ── Getters / setters ─────────────────────────────────────────────────
   
   public boolean isActive(){
      return active;
   }
   
   public void setActive(boolean v){
      active = v;
      markDirty();
   }
   
   public String getSequenceType(){
      return sequenceType;
   }
   
   public void setSequenceType(String v){
      sequenceType = v;
      markDirty();
   }
   
   public GameType getGameMode(){
      return GameType.byId(gameModeId);
   }
   
   public void setGameMode(GameType v){
      gameModeId = v.getId();
      markDirty();
   }
   
   public String getDimension(){
      return dimension;
   }
   
   public void setDimension(String v){
      dimension = v;
      markDirty();
   }
   
   public double getX(){
      return x;
   }
   
   public double getY(){
      return y;
   }
   
   public double getZ(){
      return z;
   }
   
   public void setPosition(double x, double y, double z){
      this.x = x;
      this.y = y;
      this.z = z;
      markDirty();
   }
   
   public float getYaw(){
      return yaw;
   }
   
   public float getPitch(){
      return pitch;
   }
   
   public void setRotation(float yaw, float pitch){
      this.yaw = yaw;
      this.pitch = pitch;
      markDirty();
   }
   
   public boolean wasInvulnerable(){
      return wasInvulnerable;
   }
   
   public void setWasInvulnerable(boolean v){
      wasInvulnerable = v;
      markDirty();
   }
   
   public boolean wasAllowFlying(){
      return wasAllowFlying;
   }
   
   public void setWasAllowFlying(boolean v){
      wasAllowFlying = v;
      markDirty();
   }
   
   public boolean wasFlying(){
      return wasFlying;
   }
   
   public void setWasFlying(boolean v){
      wasFlying = v;
      markDirty();
   }
   
   public String getCameraEntityUUID(){
      return cameraEntityUUID;
   }
   
   public void setCameraEntityUUID(String v){
      cameraEntityUUID = v;
      markDirty();
   }
   
   public UUID getPlayerUUID(){
      return playerUUID;
   }
}

