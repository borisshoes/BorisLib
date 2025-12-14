package net.borisshoes.borislib.utils;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class CodecUtils {
   public static final Codec<List<BlockPos>> BLOCKPOS_LIST = BlockPos.CODEC.listOf();
   public static final Codec<List<CompoundTag>> COMPOUND_LIST = CompoundTag.CODEC.listOf();
   public static final Codec<List<String>> STRING_LIST = Codec.STRING.listOf();
   public static final Codec<String[]> STRING_ARRAY = STRING_LIST.xmap(l -> l.toArray(String[]::new), Arrays::asList);
   public static final Codec<UUID> UUID_CODEC = Codec.STRING.comapFlatMap(s -> {
      try {
         return DataResult.success(UUID.fromString(s));
      } catch (IllegalArgumentException e) {
         return DataResult.error(() -> "Invalid UUID: " + s);
      }
   }, UUID::toString);
   
   public static final Codec<ItemStackWithSlot> BIG_STACK_CODEC = RecordCodecBuilder.create(
         instance -> instance.group(ExtraCodecs.NON_NEGATIVE_INT.fieldOf("Slot").orElse(0).forGetter(ItemStackWithSlot::slot), ItemStack.MAP_CODEC.forGetter(ItemStackWithSlot::stack))
               .apply(instance, ItemStackWithSlot::new)
   );
   
   public static void readBigInventory(ValueInput view, NonNullList<ItemStack> stacks){
      for (ItemStackWithSlot stackWithSlot : view.listOrEmpty("Items", BIG_STACK_CODEC)) {
         if (stackWithSlot.isValidInContainer(stacks.size())) {
            stacks.set(stackWithSlot.slot(), stackWithSlot.stack());
         }
      }
   }
   
   public static void writeBigInventory(ValueOutput view, NonNullList<ItemStack> stacks, boolean setIfEmpty) {
      ValueOutput.TypedOutputList<ItemStackWithSlot> listAppender = view.list("Items", BIG_STACK_CODEC);
      
      for (int i = 0; i < stacks.size(); i++) {
         ItemStack itemStack = stacks.get(i);
         if (!itemStack.isEmpty()) {
            listAppender.add(new ItemStackWithSlot(i, itemStack));
         }
      }
      
      if (listAppender.isEmpty() && !setIfEmpty) {
         view.discard("Items");
      }
   }
}
