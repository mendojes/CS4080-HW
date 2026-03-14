package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class Interpreter implements Expr.Visitor<Object>, Stmt.Visitor<Void> {
  final Map<String, Object> globals = new HashMap<>();
  private Environment environment = new Environment();

  private final Map<Expr, Integer> locals = new HashMap<>();
  private final Map<Expr, Integer> slots = new HashMap<>();

  Interpreter() {
    globals.put("clock", new LoxCallable() {
      @Override
      public int arity() {
        return 0;
      }

      @Override
      public Object call(Interpreter interpreter, List<Object> arguments) {
        return (double) System.currentTimeMillis() / 1000.0;
      }

      @Override
      public String toString() {
        return "<native fn>";
      }
    });
  }

  void interpret(List<Stmt> statements) {
    try {
      for (Stmt statement : statements) {
        execute(statement);
      }
    } catch (RuntimeError error) {
      Lox.runtimeError(error);
    }
  }

  void resolve(Expr expr, int depth, int slot) {
    locals.put(expr, depth);
    slots.put(expr, slot);
  }

  void executeBlock(List<Stmt> statements, Environment environment) {
    Environment previous = this.environment;
    try {
      this.environment = environment;
      for (Stmt statement : statements) {
        execute(statement);
      }
    } finally {
      this.environment = previous;
    }
  }

  private void execute(Stmt stmt) {
    stmt.accept(this);
  }

  private Object evaluate(Expr expr) {
    return expr.accept(this);
  }

  private boolean isGlobalScope() {
    return environment.enclosing == null;
  }

  @Override
  public Void visitBlockStmt(Stmt.Block stmt) {
    executeBlock(stmt.statements, new Environment(environment));
    return null;
  }

  @Override
  public Void visitExpressionStmt(Stmt.Expression stmt) {
    evaluate(stmt.expression);
    return null;
  }

  @Override
  public Void visitPrintStmt(Stmt.Print stmt) {
    Object value = evaluate(stmt.expression);
    System.out.println(stringify(value));
    return null;
  }

  @Override
  public Void visitVarStmt(Stmt.Var stmt) {
    Object value = null;
    if (stmt.initializer != null) {
      value = evaluate(stmt.initializer);
    }

    if (isGlobalScope()) {
      globals.put(stmt.name.lexeme, value);
    } else {
      environment.define(value);
    }
    return null;
  }

  @Override
  public Void visitFunctionStmt(Stmt.Function stmt) {
    LoxFunction fn = new LoxFunction(stmt, environment, false);

    if (isGlobalScope()) {
      globals.put(stmt.name.lexeme, fn);
    } else {
      environment.define(fn);
    }

    return null;
  }

  @Override
  public Void visitReturnStmt(Stmt.Return stmt) {
    Object value = null;
    if (stmt.value != null) {
      value = evaluate(stmt.value);
    }
    throw new Return(value);
  }

  @Override
  public Void visitIfStmt(Stmt.If stmt) {
    if (isTruthy(evaluate(stmt.condition))) {
      execute(stmt.thenBranch);
    } else if (stmt.elseBranch != null) {
      execute(stmt.elseBranch);
    }
    return null;
  }

  @Override
  public Void visitWhileStmt(Stmt.While stmt) {
    try {
      while (isTruthy(evaluate(stmt.condition))) {
        execute(stmt.body);
      }
    } catch (BreakException ex) {
      // Exit loop.
    }
    return null;
  }

  private static class BreakException extends RuntimeException {}

  @Override
  public Void visitBreakStmt(Stmt.Break stmt) {
    throw new BreakException();
  }

  @Override
  public Void visitClassStmt(Stmt.Class stmt) {
    Integer localSlot = null;

    if (isGlobalScope()) {
      globals.put(stmt.name.lexeme, null);
    } else {
      localSlot = environment.define(null);
    }

    Map<String, LoxFunction> classMethods = new HashMap<>();
    for (Stmt.Function method : stmt.classMethods) {
      classMethods.put(
              method.name.lexeme,
              new LoxFunction(method, environment, false)
      );
    }

    LoxClass metaclass = new LoxClass(
            null,
            stmt.name.lexeme + " metaclass",
            classMethods
    );

    Map<String, LoxFunction> methods = new HashMap<>();
    for (Stmt.Function method : stmt.methods) {
      boolean isInitializer = method.name.lexeme.equals("init");
      methods.put(
              method.name.lexeme,
              new LoxFunction(method, environment, isInitializer)
      );
    }

    LoxClass klass = new LoxClass(
            metaclass,
            stmt.name.lexeme,
            methods
    );

    // Assign the finished class object back into the declared variable.
    if (isGlobalScope()) {
      globals.put(stmt.name.lexeme, klass);
    } else {
      environment.assignAt(0, localSlot, klass);
    }

    return null;
  }
  @Override
  public Object visitLiteralExpr(Expr.Literal expr) {
    return expr.value;
  }

  @Override
  public Object visitGroupingExpr(Expr.Grouping expr) {
    return evaluate(expr.expression);
  }

  @Override
  public Object visitUnaryExpr(Expr.Unary expr) {
    Object right = evaluate(expr.right);

    switch (expr.operator.type) {
      case BANG:
        return !isTruthy(right);
      case MINUS:
        checkNumberOperand(expr.operator, right);
        return -(double) right;
    }

    return null;
  }

  @Override
  public Object visitBinaryExpr(Expr.Binary expr) {
    Object left = evaluate(expr.left);
    Object right = evaluate(expr.right);

    switch (expr.operator.type) {
      case BANG_EQUAL:
        return !isEqual(left, right);
      case EQUAL_EQUAL:
        return isEqual(left, right);
      case GREATER:
        checkNumberOperands(expr.operator, left, right);
        return (double) left > (double) right;
      case GREATER_EQUAL:
        checkNumberOperands(expr.operator, left, right);
        return (double) left >= (double) right;
      case LESS:
        checkNumberOperands(expr.operator, left, right);
        return (double) left < (double) right;
      case LESS_EQUAL:
        checkNumberOperands(expr.operator, left, right);
        return (double) left <= (double) right;
      case MINUS:
        checkNumberOperands(expr.operator, left, right);
        return (double) left - (double) right;
      case PLUS:
        if (left instanceof Double && right instanceof Double) {
          return (double) left + (double) right;
        }
        if (left instanceof String || right instanceof String) {
          return stringify(left) + stringify(right);
        }
        throw new RuntimeError(expr.operator,
                "Operands must be two numbers or two strings.");
      case SLASH:
        checkNumberOperands(expr.operator, left, right);
        if ((double) right == 0.0) {
          throw new RuntimeError(expr.operator, "Error: Division by zero.");
        }
        return (double) left / (double) right;
      case STAR:
        checkNumberOperands(expr.operator, left, right);
        return (double) left * (double) right;
    }

    return null;
  }

  @Override
  public Object visitLogicalExpr(Expr.Logical expr) {
    Object left = evaluate(expr.left);

    if (expr.operator.type == TokenType.OR) {
      if (isTruthy(left)) return left;
    } else {
      if (!isTruthy(left)) return left;
    }

    return evaluate(expr.right);
  }

  @Override
  public Object visitTernaryExpr(Expr.Ternary expr) {
    Object condition = evaluate(expr.condition);
    if (isTruthy(condition)) {
      return evaluate(expr.thenBranch);
    }
    return evaluate(expr.elseBranch);
  }

  @Override
  public Object visitVariableExpr(Expr.Variable expr) {
    return lookUpVariable(expr.name, expr);
  }

  @Override
  public Object visitThisExpr(Expr.This expr) {
    return lookUpVariable(expr.keyword, expr);
  }

  @Override
  public Object visitFunctionExpr(Expr.Function expr) {
    Token syntheticName = new Token(TokenType.IDENTIFIER, "<anonymous>", null, 0);
    Stmt.Function declaration = new Stmt.Function(syntheticName, expr);
    return new LoxFunction(declaration, environment, false);
  }

  private Object lookUpVariable(Token name, Expr expr) {
    Integer distance = locals.get(expr);
    if (distance != null) {
      return environment.getAt(distance, slots.get(expr));
    }

    if (globals.containsKey(name.lexeme)) {
      return globals.get(name.lexeme);
    }

    throw new RuntimeError(name, "Undefined variable '" + name.lexeme + "'.");
  }

  @Override
  public Object visitAssignExpr(Expr.Assign expr) {
    Object value = evaluate(expr.value);

    Integer distance = locals.get(expr);
    if (distance != null) {
      environment.assignAt(distance, slots.get(expr), value);
      return value;
    }

    if (!globals.containsKey(expr.name.lexeme)) {
      throw new RuntimeError(expr.name,
              "Undefined variable '" + expr.name.lexeme + "'.");
    }

    globals.put(expr.name.lexeme, value);
    return value;
  }

  @Override
  public Object visitCallExpr(Expr.Call expr) {
    Object callee = evaluate(expr.callee);

    List<Object> arguments = new ArrayList<>();
    for (Expr argument : expr.arguments) {
      arguments.add(evaluate(argument));
    }

    if (!(callee instanceof LoxCallable)) {
      throw new RuntimeError(expr.paren,
              "Can only call functions and classes.");
    }

    LoxCallable function = (LoxCallable) callee;
    if (arguments.size() != function.arity()) {
      throw new RuntimeError(expr.paren,
              "Expected " + function.arity() + " arguments but got " +
                      arguments.size() + ".");
    }

    return function.call(this, arguments);
  }

  @Override
  public Object visitGetExpr(Expr.Get expr) {
    Object object = evaluate(expr.object);
    if (object instanceof LoxInstance) {
      Object result = ((LoxInstance) object).get(expr.name);
      if (result instanceof LoxFunction &&
              ((LoxFunction) result).isGetter()) {
        result = ((LoxFunction) result).call(this, null);
      }

      return result;
    }

    throw new RuntimeError(expr.name,
            "Only instances have properties.");
  }

  @Override
  public Object visitSetExpr(Expr.Set expr) {
    Object object = evaluate(expr.object);
    if (!(object instanceof LoxInstance)) {
      throw new RuntimeError(expr.name, "Only instances have fields.");
    }

    Object value = evaluate(expr.value);
    ((LoxInstance) object).set(expr.name, value);
    return value;
  }

  private void checkNumberOperand(Token operator, Object operand) {
    if (operand instanceof Double) return;
    throw new RuntimeError(operator, "Operand must be a number.");
  }

  private void checkNumberOperands(Token operator, Object left, Object right) {
    if (left instanceof Double && right instanceof Double) return;
    throw new RuntimeError(operator, "Operands must be numbers.");
  }

  private boolean isTruthy(Object object) {
    if (object == null) return false;
    if (object instanceof Boolean) return (boolean) object;
    return true;
  }

  private boolean isEqual(Object a, Object b) {
    if (a == null && b == null) return true;
    if (a == null) return false;
    return a.equals(b);
  }

  private String stringify(Object object) {
    if (object == null) return "nil";

    if (object instanceof Double) {
      String text = object.toString();
      if (text.endsWith(".0")) {
        text = text.substring(0, text.length() - 2);
      }
      return text;
    }

    return object.toString();
  }

  String interpret(Expr expression) {
    try {
      Object value = evaluate(expression);
      return stringify(value);
    } catch (RuntimeError error) {
      Lox.runtimeError(error);
      return null;
    }
  }

}