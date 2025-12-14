package net.borisshoes.borislib.mixins;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Inventory.class)
public interface InventoryAccessor {
   
   @Invoker("hasRemainingSpaceForItem")
   boolean canAddMore(ItemStack existingStack, ItemStack stack);
}
