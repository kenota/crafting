package com.binarybuffer.jlox;

import static com.binarybuffer.jlox.TokenType.*;

import com.binarybuffer.jlox.Expr.Variable;
import java.util.ArrayList;
import java.util.List;

class Parser {
  private static class ParseError extends RuntimeException {}

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
      if (match(FUN)) return function("function");
      if (match(VAR)) return variable();
      return statement();
    } catch (ParseError err) {
      synchronize();
      return null;
    }
  }

  private Stmt function(String kind) {
    List<Token> args = new ArrayList<>();

    Token name = consume(IDENTIFIER, "Expect name of the " + kind);

    consume(LEFT_PAREN, "Expect ( to start argument list of the " + kind);
    if (!check(RIGHT_PAREN)) {
      do {
        if (args.size() >= 255) {
          error(peek(), "cant have more than 255 parameters");
        }
        args.add(consume(IDENTIFIER, "expecting parameter name"));
      } while (match(COMMA));
    }
    consume(RIGHT_PAREN, "expecting ')' after argument list");

    consume(LEFT_BRACE, "expecting '{' before start of " + kind + " body");
    List<Stmt> body = block();
    return new Stmt.Function(name, args, body);

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
    if (match(IF)) return ifStatement();
    if (match(PRINT)) return printStatement();
    if (match(LEFT_BRACE)) return new Stmt.Block(block());
    if (match(WHILE)) return whileStatement();
    if (match(FOR)) return forStatement();

    return expressionStatement();
  }

  // Desugaring FOR statement
  private Stmt forStatement() {
    consume(LEFT_PAREN, "Expect '(' after for ");
    //
    Stmt init = null;
    if (!check(SEMICOLON)) {
      if (check(VAR)) {
        init = declaration();
      } else {
        init = statement();
      }
    } else {
        consume(SEMICOLON, "");
    }

    // If condition is not specified, its true by default.
    Expr condition = new Expr.Literal(true);
    // Condition can be optional too, even though without support of break
    // this will mean we have unbounded loop
    if (!check(SEMICOLON)) {
      condition = expression();
    }
    // need to consume semicolon ourselves
    consume(SEMICOLON, "expect ';' after condition in a for loop");

    Stmt iter = null;
    if (!check(RIGHT_PAREN)) {
      iter = new Stmt.Expression(expression());
    }
    consume(RIGHT_PAREN, "Expecting closing ')' in for loop");
    Stmt body = statement();

    List<Stmt> res = new ArrayList<>();
    if (init != null) {
      res.add(init);
    }
    List<Stmt> whileBody = new ArrayList<>();
    whileBody.add(body);

    if (iter != null) {
      whileBody.add(iter);
    }

    res.add(new Stmt.While(condition, new Stmt.Block(whileBody)));
    return new Stmt.Block(res);
  }

  private Stmt whileStatement() {
    consume(LEFT_PAREN, "Expect '(' for condition of while statement");
    var condition = expression();
    consume(RIGHT_PAREN, "Epxect ')' in the while block");
    var stmt = statement();
    return new Stmt.While(condition, stmt);
  }

  private Stmt ifStatement() {
    consume(LEFT_PAREN, "Expect '(' to express condition expression in if");
    var condition = expression();
    consume(RIGHT_PAREN, "Expect ')' after the condition expression");

    Stmt thenBranch = statement();
    Stmt elseBranch = null;
    if (match(ELSE)) {
      elseBranch = statement();
    }

    return new Stmt.If(condition, thenBranch, elseBranch);
  }

  private List<Stmt> block() {
    List<Stmt> stmts = new ArrayList<>();
    while (!check(RIGHT_BRACE) && !isAtEnd()) {
      stmts.add(declaration());
    }
    consume(RIGHT_BRACE, "expect '}' at the end of statement block");
    return stmts;
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
    Expr expr = ternary();

    if (match(EQUAL)) {
      Token equals = previous();
      Expr value = assignment();

      if (expr instanceof Variable) {
        return new Expr.Assign(((Variable) expr).name, value);
      }

      error(equals, "invalid assign target");
    }

    return expr;
  }

  // private Expr comma() {
  //   Expr expr = ternary();
  //   while (match(COMMA)) {
  //     Token operator = previous();
  //     Expr right = ternary();
  //     expr = new Expr.Binary(expr, operator, right);
  //   }

  //   return expr;
  // }

  private Expr ternary() {
    Expr expr = or();
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

  private Expr or() {
    Expr expr = and();

    while (match(OR)) {
      var token = previous();
      Expr right = or();
      expr = new Expr.Logical(expr, token, right);
    }
    return expr;
  }

  private Expr and() {
    Expr expr = equality();

    while (match(AND)) {
      var token = previous();
      Expr right = and();
      expr = new Expr.Logical(expr, token, right);
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

    return call();
  }

  private Expr call() {
      Expr expr = primary();

      while (match(LEFT_PAREN)) {
          expr = finishCall(expr);
      }

      return expr;
  }

  private Expr finishCall(Expr expr) {
      List<Expr> arguments = new ArrayList<>();

      if (!check(RIGHT_PAREN)) {
          do {
              if (arguments.size() >= 255) {
                  error(peek(), "Can't have more than 255 arguments");
              }
              arguments.add(expression());
          } while (match(COMMA));
      }
      var paren = consume(RIGHT_PAREN, "expect ')' after arguments");

      return new Expr.Call(expr, paren, arguments);
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
        case CLASS:
        case FOR:
        case FUN:
        case IF:
        case PRINT:
        case RETURN:
        case VAR:
        case WHILE:
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
    for (var t : types) {
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
