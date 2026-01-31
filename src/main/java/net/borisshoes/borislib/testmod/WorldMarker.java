package net.borisshoes.borislib.testmod;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.borisshoes.borislib.datastorage.DataKey;
import net.borisshoes.borislib.datastorage.DataRegistry;
import net.borisshoes.borislib.datastorage.StorableData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;

import java.util.ArrayList;
import java.util.List;

import static net.borisshoes.borislib.BorisLib.MOD_ID;

public final class WorldMarker {
   public String id;
   public BlockPos pos;
   
   public WorldMarker(){
   }
   
   public WorldMarker(String id, BlockPos pos){
      this.id = id;
      this.pos = pos;
   }
   
   // Codec for individual WorldMarker (used for reading/writing lists)
   private static final Codec<BlockPos> POS_CODEC = Codec.INT.listOf().xmap(
         list -> new BlockPos(list.get(0), list.get(1), list.get(2)),
         p -> List.of(p.getX(), p.getY(), p.getZ())
   );
   
   private static final Codec<WorldMarker> MARKER_CODEC = RecordCodecBuilder.create(i -> i.group(
         Codec.STRING.fieldOf("id").forGetter(o -> o.id),
         POS_CODEC.fieldOf("pos").forGetter(o -> o.pos)
   ).apply(i, (id, pos) -> {
      WorldMarker m = new WorldMarker();
      m.id = id;
      m.pos = pos;
      return m;
   }));
   
   /**
    * Wrapper class to store a list of WorldMarkers as StorableData
    */
   public static class MarkerList extends ArrayList<WorldMarker> implements StorableData {
      public MarkerList(){
         super();
      }
      
      public MarkerList(List<WorldMarker> markers){
         super(markers);
      }
      
      @Override
      public void read(ValueInput view){
         this.clear();
         for(WorldMarker marker : view.listOrEmpty("markers", MARKER_CODEC)){
            this.add(marker);
         }
      }
      
      @Override
      public void writeNbt(CompoundTag tag){
         ListTag markerList = new ListTag();
         for(WorldMarker m : this){
            MARKER_CODEC.encodeStart(NbtOps.INSTANCE, m).result().ifPresent(markerList::add);
         }
         tag.put("markers", markerList);
      }
   }
   
   public static final DataKey<MarkerList> KEY = DataRegistry.register(
         DataKey.ofWorld(Identifier.fromNamespaceAndPath(MOD_ID, "markers"), (ResourceKey<Level> key) -> new MarkerList())
   );
}