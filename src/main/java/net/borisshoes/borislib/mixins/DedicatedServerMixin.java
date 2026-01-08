package net.borisshoes.borislib.mixins;

import net.borisshoes.borislib.BorisLib;
import net.borisshoes.borislib.datastorage.DataAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DedicatedServer.class)
public class DedicatedServerMixin {
   
   // This should be the earliest possible moment the server becomes fully available for BorisLib to use
   @Inject(method = "initServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/dedicated/DedicatedServer;setPlayerList(Lnet/minecraft/server/players/PlayerList;)V"))
   private void borislib$initializeServer(CallbackInfoReturnable<Boolean> cir){
      MinecraftServer server = (MinecraftServer) (Object) this;
      BorisLib.SERVER = server;
      DataAccess.onServerStarting(server);
   }
}
