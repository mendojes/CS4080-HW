package com.craftinginterpreters.lox;

class RpnPrinter implements Expr.Visitor<String> {

    String print(Expr expr) {
        return expr.accept(this);
    }

    @Override
    public String visitBinaryExpr(Expr.Binary expr) {
        // In RPN: left right op
        return (expr.left.accept(this) + " " + expr.right.accept(this) + " " + expr.operator.lexeme);
    }

    @Override
    public String visitGroupingExpr(Expr.Grouping expr) {
        // Parentheses don't exist in RPN; grouping just returns the inner expression.
        return expr.expression.accept(this);
    }

    @Override
    public String visitLiteralExpr(Expr.Literal expr) {
        if (expr.value == null) return "nil";
        return expr.value.toString();
    }

    @Override
    public String visitUnaryExpr(Expr.Unary expr) {
        // In RPN for unary: operand op
        // (e.g., -3 becomes "3 -")
        return (expr.right.accept(this) + " " + expr.operator.lexeme);
    }
    
    public static void main(String[] args) {
        Expr expression = new Expr.Binary(
                new Expr.Grouping(
                        new Expr.Binary(
                                new Expr.Literal(1),
                                new Token(TokenType.PLUS, "+", null, 1),
                                new Expr.Literal(2))),
                new Token(TokenType.STAR, "*", null, 1),
                new Expr.Grouping(new Expr.Binary(new Expr.Literal(4),
                        new Token(TokenType.MINUS, "—", null, 1),
                        new Expr.Literal(3))));
        System.out.println(new RpnPrinter().print(expression));
    }
}
