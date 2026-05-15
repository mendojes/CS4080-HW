package com.craftinginterpreters.lox;

import java.util.List;

class LoxFunction implements LoxCallable {
    private final Stmt.Function declaration;
    private final Environment closure;
    private final boolean isInitializer;

    LoxFunction(Stmt.Function declaration, Environment closure, boolean isInitializer) {
        this.declaration = declaration;
        this.closure = closure;
        this.isInitializer = isInitializer;
    }

    LoxFunction bind(LoxInstance instance, LoxFunction inner) {
        Environment environment = new Environment(closure);
        environment.define(instance); // slot 0 == this
        environment.define(inner);    // slot 1 == inner
        return new LoxFunction(declaration, environment, isInitializer);
    }

    @Override
    public int arity() {
        List<Token> params = declaration.function.params;
        return params == null ? 0 : params.size();
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        Environment environment = new Environment(closure);

        List<Token> params = declaration.function.params;
        if (params != null) {
            for (int i = 0; i < params.size(); i++) {
                environment.define(arguments.get(i));
            }
        }

        try {
            interpreter.executeBlock(declaration.function.body, environment);
        } catch (Return returnValue) {
            if (isInitializer) return closure.getAt(0, 0); // "this"
            return returnValue.value;
        }

        if (isInitializer) return closure.getAt(0, 0);
        return null;
    }

    boolean isGetter() {
        return declaration.function.params == null;
    }

    @Override
    public String toString() {
        return "<fn " + declaration.name.lexeme + ">";
    }
}