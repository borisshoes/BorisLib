package net.borisshoes.borislib.mixins;

import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientboundSectionBlocksUpdatePacket.class)
public interface SectionBlocksUpdatePacketAccessor {
   @Accessor("sectionPos")
   SectionPos getSectionPos();
   
   @Accessor("positions")
   short[] getPositions();
}

