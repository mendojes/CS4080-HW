package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.List;

class Environment {
    final Environment enclosing;
    private final List<Object> values = new ArrayList<>();

    Environment() {
        enclosing = null;
    }

    Environment(Environment enclosing) {
        this.enclosing = enclosing;
    }

    void define(Object value) {
        values.add(value);
    }

    Object getAt(int distance, int slot) {
        return ancestor(distance).values.get(slot);
    }

    void assignAt(int distance, int slot, Object value) {
        ancestor(distance).values.set(slot, value);
    }
    Environment ancestor(int distance) {
        Environment environment = this;
        for (int i = 0; i < distance; i++) {
            environment = environment.enclosing;
        }

        return environment; 
    }

}
