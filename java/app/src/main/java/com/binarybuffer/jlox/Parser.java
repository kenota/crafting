package com.binarybuffer.jlox;

import static com.binarybuffer.jlox.TokenType.*;

import java.util.ArrayList;
import java.util.List;

import com.binarybuffer.jlox.Expr.Variable;



class Parser {
    private static class ParseError extends RuntimeException {};
    private final List<Token> tokens;
    private int current = 0;

    Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    List<Stmt> parse() {
        try {
            List<Stmt> statements = new ArrayList<>();

            while (!isAtEnd()) {
                statements.add(declaration());
            }

            return statements;
        } catch (ParseError err) {
            return null;
        }
    }

    private Stmt declaration() {
        try {
            if (match(VAR)) return variable();
            return statement();
        } catch (ParseError err) {
            synchronize();
            return null;
        }
    }

    private Stmt variable() {
        var identifier = consume(IDENTIFIER, "expect identifier");

        Expr initializer = null;
        if (match(EQUAL)) {
            initializer = expression();
        }

        consume(SEMICOLON, "expect ';' at the end of variable declaration");
        return new Stmt.Var(identifier, initializer);
    }

    private Stmt statement() {
        if (match(PRINT)) return printStatement();
        if (match(LEFT_BRACE)) return block();

        return expressionStatement();
    }

    private Stmt block() {
        List<Stmt> stmts = new ArrayList<>();
        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            stmts.add(declaration());
        }
        consume(RIGHT_BRACE, "expect '}' at the end of statement block");
        return new Stmt.Block(stmts);
    }

    private Stmt printStatement() {
        Expr value = expression();
        consume(SEMICOLON, "Expect ';' after value");
        return new Stmt.Print(value);
    }

    private Stmt expressionStatement() {
        Expr expr = expression();
        consume((SEMICOLON), "Expect ';' after expression");
        return new Stmt.Expression(expr);
    }

    private Expr expression() {
        return assignment();
    }

    private Expr assignment() {
        Expr expr = comma();

        if (match(EQUAL)) {
            Token equals = previous();
            Expr value = assignment();

            if (expr instanceof Variable) {
                return new Expr.Assign(((Variable)expr).name, value);
            }

            error(equals, "invalid assign target");
        }

        return expr;
    }

    private Expr comma() {
        Expr expr = ternary();
        while (match(COMMA)) {
            Token operator = previous();
            Expr right = ternary();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr ternary() {
        Expr expr = equality();
        while (match(QUESTION)) {
            Expr truthy = expression();
            if (!match(COLON)) {
                error(peek(), "expect : to finish ternary ?: operator");
            }
            Expr falsy = expression();
            expr = new Expr.Condition(expr, truthy, falsy);
        }
        return expr;
    }

    private Expr equality() {
        Expr expr = comparison();
        while (match(BANG_EQUAL, EQUAL_EQUAL)) {
            Token operator = previous();
            Expr right = comparison();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr comparison() {
        Expr expr = term();

        while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            Token operator = previous();
            Expr right = term();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr term() {
        Expr expr = factor();

        while (match(MINUS, PLUS)) {
            Token operator = previous();
            Expr right = factor();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr factor() {
        Expr expr = unary();

        while (match(STAR, SLASH)) {
            Token operator = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr unary() {
        if (match(BANG, MINUS)) {
            Token operator = previous();
            Expr right = unary();
            return new Expr.Unary(operator, right);
        }

        return primary();
    }

    private Expr primary() {
        if (match(FALSE)) return new Expr.Literal(false);
        if (match(TRUE)) return new Expr.Literal(true);
        if (match(NIL)) return new Expr.Literal(null);

        if (match(NUMBER, STRING)) {
            return new Expr.Literal(previous().literal());
        }

        if (match(LEFT_PAREN)) {
            Expr expr = expression();
            consume(RIGHT_PAREN, "Expect ')' after expression.");
            return new Expr.Grouping(expr);
        }

        if (match(IDENTIFIER)) {
            return new Expr.Variable(previous());
        }

        throw error(peek(), "Expect expression");
    }

    private void synchronize() {
        advance();
        while (!isAtEnd()) {
            if (previous().type() == SEMICOLON) return;

            switch (peek().type()) {
                case CLASS: case FOR: case FUN: case IF: case PRINT:
                case RETURN: case VAR: case WHILE:
                return;
            }
            advance();
        }
    }

    private Token consume(TokenType type, String message) {
        if (check(type)) {
            return advance();
        }
        throw error(peek(), message);
    }

    private ParseError error(Token token, String message) {
        Lox.error(token, message);
        return new ParseError();
    }

    boolean match(TokenType... types) {
        for (var t: types) {
            if (check(t)) {
                advance();
                return true;
            }
        }

        return false;
    }

    boolean check(TokenType type) {
        return this.tokens.get(current).type() == type;
    }

    boolean isAtEnd() {
        return this.tokens.get(current).type() == EOF;
    }

    // Advance IFF not at the end already
    Token advance() {
        if (!isAtEnd()) {
            current++;
        }
        return previous();
    }

    Token previous() {
        return this.tokens.get(current - 1);
    }

    Token peek() {
        return tokens.get(current);
    }

}
