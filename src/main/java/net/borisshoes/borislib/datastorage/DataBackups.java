package net.borisshoes.borislib.datastorage;

import net.borisshoes.borislib.BorisLib;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static net.borisshoes.borislib.BorisLib.MOD_ID;

/**
 * Independent, crash-safe on-disk backups for GLOBAL and WORLD saved data.
 *
 * <p>Vanilla's {@code SavedDataStorage} writes GLOBAL/WORLD {@code .dat} files non-atomically
 * (plain {@code NbtIo.writeCompressed}, no temp+rename), with no backup and no emptiness check.
 * A hard reboot mid-write, or an encode that produced an empty tag, therefore results in
 * unrecoverable data loss — which is exactly what player data avoids via its {@code .dat_old}
 * rotation. This class gives GLOBAL/WORLD the same protection.</p>
 *
 * <p>Backups live under {@code <worldRoot>/data/<modid>_backups/}. They are encoded on the main
 * thread inside the {@code BEFORE_SAVE} hook, straight from the live in-memory state, so they are
 * never stale relative to what the server believes it saved; compressing and writing happens on a
 * single background thread ({@link #writeAsync}) so it keeps the order of saves without stalling the
 * tick. Each write is atomic (temp file + rotate previous to {@code .dat_old} + rename) so an
 * interrupted write can always fall back to the previous good copy.</p>
 */
public final class DataBackups {
   private static volatile Path dir;
   private static volatile ExecutorService writer;
   // Last tag queued per backup name, so a save that changed nothing does not rotate away the older copy
   private static final Map<String, CompoundTag> LAST_QUEUED = new ConcurrentHashMap<>();
   
   private DataBackups(){
   }
   
   /**
    * Points the backup system at a world's root directory, creating the backup folder.
    * Must be called once on server start before any global/world data is saved or restored.
    *
    * @param worldRoot the world's root directory
    */
   public static void init(Path worldRoot){
      try{
         Path d = worldRoot.resolve("data").resolve(MOD_ID + "_backups");
         Files.createDirectories(d);
         if(!d.equals(dir)) LAST_QUEUED.clear();
         dir = d;
         if(writer == null){
            writer = Executors.newSingleThreadExecutor(runnable -> {
               Thread thread = new Thread(runnable, "BorisLib Data Backups");
               thread.setDaemon(true);
               return thread;
            });
         }
         BorisLib.LOGGER.info("BorisLib global/world data backups initialized at {}", d);
      }catch(Exception e){
         BorisLib.LOGGER.error("Failed to initialize BorisLib data backup directory: {}", e.getMessage());
         dir = null;
      }
   }
   
   /** @return {@code true} once {@link #init(Path)} has successfully run. */
   public static boolean available(){
      return dir != null;
   }
   
   /** @return the backup base name for GLOBAL data. */
   public static String globalName(){
      return GlobalState.FILE_ID;
   }
   
   /**
    * @param worldKey the dimension
    * @return the backup base name for a WORLD dimension (filesystem-safe).
    */
   public static String worldName(ResourceKey<Level> worldKey){
      Identifier id = worldKey.identifier();
      String sanitized = (id.getNamespace() + "_" + id.getPath()).replaceAll("[^a-zA-Z0-9._-]", "_");
      return WorldState.FILE_ID + "." + sanitized;
   }
   
   /**
    * Queues {@link #write} on the backup thread. Skipped when {@code root} equals the last tag queued under this
    * name. The tag is copied first: a mod's {@code writeNbt} may have put a tag it keeps mutating, which must not
    * change while the backup thread serializes it. Falls back to writing immediately if the backup thread is not
    * running.
    *
    * @param name base file name (without extension)
    * @param root the encoded namespaced tag to persist
    */
   public static void writeAsync(String name, @Nullable CompoundTag root){
      if(dir == null || root == null) return;
      if(root.equals(LAST_QUEUED.get(name))) return;
      CompoundTag snapshot = root.copy();
      LAST_QUEUED.put(name, snapshot);
      ExecutorService w = writer;
      if(w == null){
         writeOrForget(name, snapshot);
         return;
      }
      try{
         w.execute(() -> writeOrForget(name, snapshot));
      }catch(Exception e){
         writeOrForget(name, snapshot);
      }
   }

   // A failed write must not count as queued, or the same data would never be retried
   private static void writeOrForget(String name, CompoundTag root){
      if(!writeInternal(name, root)) LAST_QUEUED.remove(name, root);
   }

   /**
    * Waits for every queued backup to be written, then stops the backup thread. Called when the server stops;
    * {@link #init} starts a new thread for the next world.
    */
   public static void flush(){
      ExecutorService w = writer;
      writer = null;
      if(w == null) return;
      w.shutdown();
      try{
         if(!w.awaitTermination(30, TimeUnit.SECONDS)){
            BorisLib.LOGGER.error("Timed out waiting for BorisLib data backups to finish writing");
         }
      }catch(InterruptedException e){
         Thread.currentThread().interrupt();
      }
   }

   /**
    * Atomically writes {@code root} to {@code <dir>/<name>.dat}, rotating any existing file to
    * {@code .dat_old} first. Writing an empty tag is allowed and meaningful — it records that the
    * live state is legitimately empty, which lets {@link #read(String)}-based restore distinguish
    * "cleared on purpose" from "file was corrupted".
    *
    * @param name base file name (without extension)
    * @param root the encoded namespaced tag to persist (may be empty, must be non-null)
    */
   public static void write(String name, @Nullable CompoundTag root){
      writeInternal(name, root);
   }

   /** {@link #write}, reporting whether the backup was written. */
   private static boolean writeInternal(String name, @Nullable CompoundTag root){
      Path d = dir;
      if(d == null || root == null) return false;
      Path main = d.resolve(name + ".dat");
      Path backup = d.resolve(name + ".dat_old");
      Path temp = d.resolve(name + ".dat_tmp");
      try{
         try(OutputStream os = Files.newOutputStream(temp); GZIPOutputStream gz = new GZIPOutputStream(os); DataOutputStream out = new DataOutputStream(gz)){
            NbtIo.writeUnnamedTagWithFallback(root, out);
         }
         if(Files.exists(main)){
            try{
               Files.move(main, backup, StandardCopyOption.REPLACE_EXISTING);
            }catch(Exception ex){
               BorisLib.LOGGER.warn("Failed to rotate data backup {} to .dat_old, continuing: {}", name, ex.getMessage());
            }
         }
         Files.move(temp, main, StandardCopyOption.REPLACE_EXISTING);
         return true;
      }catch(Exception e){
         BorisLib.LOGGER.error("Failed to write data backup {}: {}", name, e.getMessage());
         try{
            Files.deleteIfExists(temp);
         }catch(Exception ignored){
         }
         return false;
      }
   }
   
   /**
    * Reads {@code <dir>/<name>.dat}, transparently falling back to {@code .dat_old} if the main
    * file is missing or corrupted.
    *
    * @param name base file name (without extension)
    * @return the decoded tag, or {@code null} if neither file could be read
    */
   @Nullable
   public static CompoundTag read(String name){
      Path d = dir;
      if(d == null) return null;
      Path main = d.resolve(name + ".dat");
      Path backup = d.resolve(name + ".dat_old");
      
      if(Files.exists(main)){
         try(DataInputStream in = new DataInputStream(new GZIPInputStream(Files.newInputStream(main)))){
            return NbtIo.read(in);
         }catch(Exception e){
            BorisLib.LOGGER.warn("Failed to read data backup {}, trying .dat_old: {}", name, e.getMessage());
         }
      }
      if(Files.exists(backup)){
         try(DataInputStream in = new DataInputStream(new GZIPInputStream(Files.newInputStream(backup)))){
            BorisLib.LOGGER.info("Recovered data backup {} from .dat_old", name);
            return NbtIo.read(in);
         }catch(Exception e){
            BorisLib.LOGGER.error("Failed to read data backup {} .dat_old as well: {}", name, e.getMessage());
         }
      }
      return null;
   }
}
