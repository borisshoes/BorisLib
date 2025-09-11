package net.borisshoes.borislib.config;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.registry.Registry;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;

import java.io.*;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static net.borisshoes.borislib.BorisLib.LOGGER;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

@SuppressWarnings({"unchecked","rawtypes"})
public class ConfigManager {
   public Set<ConfigValue> values;
   private final File file;
   private final String modId;
   private final String modName;
   
   public ConfigManager(String modId, String modName, String fileName, Registry<IConfigSetting<?>> configRegistry){
      this.modId = modId;
      this.modName = modName;
      this.file = FabricLoader.getInstance().getConfigDir().resolve(fileName).toFile();
      this.values = configRegistry.stream().map(IConfigSetting::makeConfigValue).collect(Collectors.toCollection(HashSet::new));
      this.read();
      this.save();
   }
   
   public void read(){
      try(BufferedReader input = new BufferedReader(new InputStreamReader(new FileInputStream(file)))){
         LOGGER.debug("Reading {} config...",modName);
         
         for(ConfigValue value : this.values){
            value.setValue(value.defaultValue);
         }
         
         while(input.ready()){
            String configLine = input.readLine();
            String trimmed = configLine.trim();
            if(trimmed.isEmpty()) continue;
            char firstChar = trimmed.charAt(0);
            
            if(firstChar == '!' || firstChar == '#') continue;
            if(!configLine.contains("=")) continue;
            
            int splitIndex = configLine.indexOf('=');
            String valueName = configLine.substring(0, splitIndex).strip();
            String valueValue = configLine.substring(splitIndex + 1).strip();
            
            for(ConfigValue value : this.values){
               if(!valueName.equals(value.name)) continue;
               Object defaultValue = value.defaultValue;
               try{
                  value.setValue(value.getFromString(valueValue));
               }catch(Exception e){
                  value.setValue(defaultValue);
               }
            }
         }
         
      }catch(FileNotFoundException ignored){
         LOGGER.debug("Initialising {} config...",modName);
         this.values.forEach(value -> value.value = value.defaultValue);
      }catch(IOException e){
         LOGGER.fatal("Failed to load {} config file!",modName);
         LOGGER.fatal(e);
      }catch(Exception e){
         LOGGER.fatal("Failed to parse {} config",modName);
         LOGGER.fatal(e);
      }
   }
   
   public void save(){
      LOGGER.debug("Updating {} config...",modName);
      try(BufferedWriter output = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file)))){
         output.write("# "+modName+" Configuration File" + " | " + new Date());
         output.newLine();
         output.newLine();
         
         for(ConfigValue value : this.values){
            if(value.getComment(this.modId) != null){
               output.write("# "+ Text.translatable(value.getComment(this.modId)).getString());
               output.newLine();
            }
            output.write(value.name + " = " + value.getValueString());
            output.newLine();
         }
      }catch(IOException e){
         LOGGER.fatal("Failed to save {} config file!",modName);
         LOGGER.fatal(e);
      }
   }
   
   public LiteralArgumentBuilder<ServerCommandSource> generateCommand(String prefixA, String prefixB){
      LiteralArgumentBuilder<ServerCommandSource> out =
            literal(prefixA).then(literal(prefixB).requires(source -> source.hasPermissionLevel(2))
                  .executes(ctx -> {
                     values.forEach(value ->
                           ctx.getSource().sendFeedback(()-> MutableText.of(new TranslatableTextContent(ConfigValue.getTranslation(value.getName(),this.modId,"getter_setter"), null, new String[] {String.valueOf(value.getValueString())})), false));
                     return 1;
                  }));
      values.forEach(value ->
            out.then(literal(prefixB).then(literal(value.name)
                  .executes(ctx -> {
                     ctx.getSource().sendFeedback(()->MutableText.of(new TranslatableTextContent(ConfigValue.getTranslation(value.getName(),this.modId,"getter_setter"), null, new String[] {String.valueOf(value.getValueString())})), false);
                     return 1;
                  })
                  .then(argument(value.name, value.getArgumentType()).suggests(value::getSuggestions)
                        .executes(ctx -> {
                           value.value = value.parseArgumentValue(ctx);
                           ((CommandContext<ServerCommandSource>) ctx).getSource().sendFeedback(()->MutableText.of(new TranslatableTextContent(ConfigValue.getTranslation(value.getName(),this.modId,"getter_setter"), null, new String[] {String.valueOf(value.getValueString())})), true);
                           this.save();
                           return 1;
                        })))));
      return out;
   }
   
   
   public Object getValue(String name){
      return values.stream().filter(value -> value.name.equals(name)).findFirst().map(iConfigValue -> iConfigValue.value).orElse(null);
   }
   
   public int getInt(IConfigSetting<?> setting){
      try{
         return (int) this.getValue(setting.getName());
      }catch(Exception e){
         LOGGER.error("Failed to get Integer config for {}:{}", this.modId, setting.getName());
         LOGGER.error(e.toString());
      }
      return 0;
   }
   
   public boolean getBoolean(IConfigSetting<?> setting){
      try{
         return (boolean) this.getValue(setting.getName());
      }catch(Exception e){
         LOGGER.error("Failed to get Boolean config for {}:{}", this.modId, setting.getName());
         LOGGER.error(e.toString());
      }
      return false;
   }
   
   public double getDouble(IConfigSetting<?> setting){
      try{
         return (double) this.getValue(setting.getName());
      }catch(Exception e){
         LOGGER.error("Failed to get Double config for {}:{}", this.modId, setting.getName());
         LOGGER.error(e.toString());
      }
      return 0;
   }
}
