package com.binarybuffer.jlox;

import java.util.HashMap;
import java.util.Map;

class Environment {
    private final Environment enclosing;
    private final Map<String, Object> values = new HashMap<>();

    Environment() {
        this.enclosing = null;
    }

    Environment(Environment enclosing) {
        this.enclosing = enclosing;
    }

    void define(String key, Object val) {
        values.put(key, val);
    }

    Object get(Token name) {
        Environment curr = this;
        while (curr != null) {
            if (curr.values.containsKey(name.lexeme())) {
                return curr.values.get(name.lexeme());
            }
            curr = curr.enclosing;
        }
        throw new RuntimeError(name, "Undefined variable '" + name.lexeme() +"'.");
    }

    void assign(Token name, Object val) {
        if (values.containsKey(name.lexeme())) {
            values.put(name.lexeme(), val);
        } else {
            if (enclosing != null) {
                enclosing.assign(name, val);
                return;
            }
            throw new RuntimeError(name, "assignment to undeclared variable");
        }
    }
}
