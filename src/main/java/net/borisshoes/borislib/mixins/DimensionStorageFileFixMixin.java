package net.borisshoes.borislib.mixins;

import com.mojang.datafixers.schemas.Schema;
import net.borisshoes.borislib.datastorage.GlobalState;
import net.borisshoes.borislib.datastorage.WorldState;
import net.minecraft.util.filefix.FileFix;
import net.minecraft.util.filefix.fixes.DimensionStorageFileFix;
import net.minecraft.util.filefix.operations.FileFixOperations;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;

/**
 * Hooks into Minecraft's world upgrade file-fix system to migrate BorisLib saved data files
 * from the 1.21.x flat directory layout to the 26.1 namespaced/dimension layout.
 * <p>
 * Minecraft's {@link DimensionStorageFileFix} only moves vanilla saved data files (scoreboard,
 * raids, maps, etc.). This mixin appends operations for BorisLib's GlobalState and WorldState
 * files using the same {@code groupMove} pattern vanilla uses.
 * <p>
 * Migration paths (per vanilla dimension):
 * <ul>
 *   <li>{@code data/<name>.dat} → {@code dimensions/minecraft/overworld/data/minecraft/<name>.dat}</li>
 *   <li>{@code DIM-1/data/<name>.dat} → {@code dimensions/minecraft/the_nether/data/minecraft/<name>.dat}</li>
 *   <li>{@code DIM1/data/<name>.dat} → {@code dimensions/minecraft/the_end/data/minecraft/<name>.dat}</li>
 * </ul>
 */
@Mixin(DimensionStorageFileFix.class)
public abstract class DimensionStorageFileFixMixin extends FileFix {
   
   public DimensionStorageFileFixMixin(Schema schema){
      super(schema);
   }
   
   @Inject(method = "makeFixer", at = @At("HEAD"))
   private void borislib$addModFileOperations(CallbackInfo ci){
      // Move borislib saved data files from old flat/dimension paths to new namespaced/dimension paths.
      // Uses the same groupMove pattern that vanilla uses for raids, world_border, etc.
      // Injected at HEAD so our files are moved BEFORE vanilla's delete("DIM-1")/delete("DIM1")
      // operations, which run at the end of the vanilla method and fail if the directories aren't empty.
      addFileFixOperation(FileFixOperations.groupMove(
            Map.of(
                  "data", "dimensions/minecraft/overworld/data/minecraft",
                  "DIM-1/data", "dimensions/minecraft/the_nether/data/minecraft",
                  "DIM1/data", "dimensions/minecraft/the_end/data/minecraft"
            ),
            List.of(
                  FileFixOperations.moveSimple(GlobalState.FILE_ID + ".dat"),
                  FileFixOperations.moveSimple(WorldState.FILE_ID + ".dat")
            )
      ));
   }
}


