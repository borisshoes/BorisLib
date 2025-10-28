package net.borisshoes.borislib.utils;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.inventory.StackWithSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.dynamic.Codecs;
import net.minecraft.util.math.BlockPos;

import java.util.Arrays;
import java.util.List;

public class CodecUtils {
   public static final Codec<List<BlockPos>> BLOCKPOS_LIST = BlockPos.CODEC.listOf();
   public static final Codec<List<NbtCompound>> COMPOUND_LIST = NbtCompound.CODEC.listOf();
   public static final Codec<List<String>> STRING_LIST = Codec.STRING.listOf();
   public static final Codec<String[]> STRING_ARRAY = STRING_LIST.xmap(l -> l.toArray(String[]::new), Arrays::asList);
   
   public static final Codec<StackWithSlot> BIG_STACK_CODEC = RecordCodecBuilder.create(
         instance -> instance.group(Codecs.NON_NEGATIVE_INT.fieldOf("Slot").orElse(0).forGetter(StackWithSlot::slot), ItemStack.MAP_CODEC.forGetter(StackWithSlot::stack))
               .apply(instance, StackWithSlot::new)
   );
   
   public static void readBigInventory(ReadView view, DefaultedList<ItemStack> stacks){
      for (StackWithSlot stackWithSlot : view.getTypedListView("Items", BIG_STACK_CODEC)) {
         if (stackWithSlot.isValidSlot(stacks.size())) {
            stacks.set(stackWithSlot.slot(), stackWithSlot.stack());
         }
      }
   }
   
   public static void writeBigInventory(WriteView view, DefaultedList<ItemStack> stacks, boolean setIfEmpty) {
      WriteView.ListAppender<StackWithSlot> listAppender = view.getListAppender("Items", BIG_STACK_CODEC);
      
      for (int i = 0; i < stacks.size(); i++) {
         ItemStack itemStack = stacks.get(i);
         if (!itemStack.isEmpty()) {
            listAppender.add(new StackWithSlot(i, itemStack));
         }
      }
      
      if (listAppender.isEmpty() && !setIfEmpty) {
         view.remove("Items");
      }
   }
}
