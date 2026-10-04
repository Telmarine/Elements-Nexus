package com.tac5studios.elementsnexus.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets holograms change a text display's text (the setter is private). */
@Mixin(Display.TextDisplay.class)
public interface TextDisplayAccessor {

    @Invoker("setText")
    void nexus$setText(Component text);
}
