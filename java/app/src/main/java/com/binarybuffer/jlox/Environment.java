package com.binarybuffer.jlox;

import java.util.HashMap;
import java.util.Map;

class Environment {
    private final Map<String, Object> values = new HashMap<>();

    void define(String key, Object val) {
        values.put(key, val);
    }

    Object get(Token name) {
        if (values.containsKey(name.lexeme())) {
            return values.get(name.lexeme());
        }
        throw new RuntimeError(name, "Undefined variable '" + name.lexeme() +"'.");
    }
}
