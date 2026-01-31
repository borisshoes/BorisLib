package net.borisshoes.borislib.datastorage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.storage.ValueInput;

/**
 * Interface for data objects that can be serialized/deserialized.
 * Reading uses ValueInput for partial decode resilience - if one field fails, others can still load.
 * Writing uses CompoundTag directly for simplicity and compatibility.
 */
public interface StorableData {
   /**
    * Reads data from the given ValueInput view.
    * Each field should be read independently with appropriate defaults for missing/corrupted values.
    *
    * @param view The ValueInput to read from
    */
   void read(ValueInput view);
   
   /**
    * Writes data to the given CompoundTag.
    *
    * @param tag The CompoundTag to write to
    */
   void writeNbt(CompoundTag tag);
}
