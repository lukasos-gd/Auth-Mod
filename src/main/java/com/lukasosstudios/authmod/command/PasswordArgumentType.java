package com.lukasosstudios.authmod.command;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

public final class PasswordArgumentType implements ArgumentType<String> {
    private static final PasswordArgumentType INSTANCE = new PasswordArgumentType();

    private PasswordArgumentType() {
    }

    public static PasswordArgumentType password() {
        return INSTANCE;
    }

    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        while (reader.canRead() && reader.peek() != ' ') {
            reader.skip();
        }
        return reader.getString().substring(start, reader.getCursor());
    }
}
