package net.borisshoes.borislib.testmod;

import net.borisshoes.borislib.datastorage.DataKey;
import net.borisshoes.borislib.datastorage.DataRegistry;
import net.borisshoes.borislib.datastorage.StorableData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.ValueInput;

import static net.borisshoes.borislib.BorisLib.MOD_ID;

public class GlobalTimestamp implements StorableData {
   public long timestamp = System.currentTimeMillis();
   
   @Override
   public void read(ValueInput view){
      this.timestamp = view.getLong("timestamp").orElse(System.currentTimeMillis());
   }
   
   @Override
   public void writeNbt(CompoundTag tag){
      tag.putLong("timestamp", timestamp);
   }
   
   public static final DataKey<GlobalTimestamp> KEY = DataRegistry.register(DataKey.ofGlobal(Identifier.fromNamespaceAndPath(MOD_ID, "timestamp"), GlobalTimestamp::new));
}
