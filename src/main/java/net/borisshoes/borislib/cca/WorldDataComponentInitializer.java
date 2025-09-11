package net.borisshoes.borislib.cca;

import net.minecraft.network.packet.s2c.play.ServerMetadataS2CPacket;
import net.minecraft.network.packet.s2c.query.PingResultS2CPacket;
import net.minecraft.network.packet.s2c.query.QueryResponseS2CPacket;
import net.minecraft.server.ServerMetadata;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistryV3;
import org.ladysnake.cca.api.v3.world.WorldComponentFactoryRegistry;
import org.ladysnake.cca.api.v3.world.WorldComponentInitializer;

import static net.borisshoes.borislib.BorisLib.MOD_ID;

public class WorldDataComponentInitializer implements WorldComponentInitializer {
   public static final ComponentKey<ILoginCallbackComponent> LOGIN_CALLBACK_LIST = ComponentRegistryV3.INSTANCE.getOrCreate(Identifier.of(MOD_ID, "login_callbacks"), ILoginCallbackComponent.class);
   
   @Override
   public void registerWorldComponentFactories(WorldComponentFactoryRegistry registry){
      registry.registerFor(ServerWorld.OVERWORLD, LOGIN_CALLBACK_LIST, LoginCallbackComponent.class, world -> new LoginCallbackComponent());
   }
}
