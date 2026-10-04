package com.tac5studios.elementsnexus.mixin;

import com.tac5studios.elementsnexus.util.ConsoleName;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

/** The console's name in /say, /tell, command feedback and anything else that uses it. */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerConsoleMixin {

    @ModifyConstant(method = "createCommandSourceStack", constant = @Constant(stringValue = "Server"))
    private String nexus$consoleTextName(String original) {
        return ConsoleName.plain();
    }

    @Redirect(method = "createCommandSourceStack", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/chat/Component;literal(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"))
    private MutableComponent nexus$consoleDisplayName(String text) {
        return Component.empty().append(ConsoleName.component());
    }
}
