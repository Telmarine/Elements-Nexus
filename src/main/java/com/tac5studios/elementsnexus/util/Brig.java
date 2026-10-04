package com.tac5studios.elementsnexus.util;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.function.Predicate;

/** Small Brigadier helper: remove a vanilla command so ours can take its name. */
public final class Brig {

    private Brig() {}

    public static <S> void remove(CommandDispatcher<S> dispatcher, String name) {
        CommandNode<S> root = dispatcher.getRoot();
        for (String field : new String[]{"children", "literals", "arguments"}) {
            try {
                Field f = CommandNode.class.getDeclaredField(field);
                f.setAccessible(true);
                ((Map<?, ?>) f.get(root)).remove(name);
            } catch (ReflectiveOperationException ignored) {
            }
        }
    }

    /** Add an extra requirement to an existing root command (keeps everything else). Returns false if not found. */
    public static <S> boolean restrict(CommandDispatcher<S> dispatcher, String name, Predicate<S> extra) {
        CommandNode<S> node = dispatcher.getRoot().getChild(name);
        if (node == null) return false;
        LiteralArgumentBuilder<S> b = LiteralArgumentBuilder.<S>literal(node.getName())
                .requires(node.getRequirement().and(extra));
        if (node.getCommand() != null) b.executes(node.getCommand());
        if (node.getRedirect() != null) {
            b.forward(node.getRedirect(), node.getRedirectModifier(), node.isFork());
        } else {
            for (CommandNode<S> child : node.getChildren()) b.then(child);
        }
        remove(dispatcher, name);
        dispatcher.getRoot().addChild(b.build());
        return true;
    }
}
