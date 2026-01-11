package net.borisshoes.borislib.mixins;

import net.borisshoes.borislib.BorisLib;
import net.borisshoes.borislib.datastorage.DataAccess;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(IntegratedServer.class)
public class IntegratedServerMixin {
   
   // This should be the earliest possible moment the server becomes fully available for BorisLib to use
   @Inject(method = "initServer", at = @At(value = "HEAD"))
   private void borislib$initializeServer(CallbackInfoReturnable<Boolean> cir){
      MinecraftServer server = (MinecraftServer) (Object) this;
      BorisLib.SERVER = server;
      DataAccess.onServerStarting(server);
   }
}
