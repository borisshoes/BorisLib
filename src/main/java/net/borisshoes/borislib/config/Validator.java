package net.borisshoes.borislib.config;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;

import java.util.function.BiConsumer;
import java.util.function.Predicate;

public record Validator<T>(Predicate<T> predicate, BiConsumer<T, CommandContext<CommandSourceStack>> onFail) {
}
