package com.binarybuffer.jlox;

import static com.binarybuffer.jlox.TokenType.AND;
import static com.binarybuffer.jlox.TokenType.OR;

import java.util.ArrayList;
import java.util.List;

import com.binarybuffer.jlox.Expr.Assign;
import com.binarybuffer.jlox.Expr.Binary;
import com.binarybuffer.jlox.Expr.Call;
import com.binarybuffer.jlox.Expr.Condition;
import com.binarybuffer.jlox.Expr.Grouping;
import com.binarybuffer.jlox.Expr.Literal;
import com.binarybuffer.jlox.Expr.Logical;
import com.binarybuffer.jlox.Expr.Unary;
import com.binarybuffer.jlox.Expr.Variable;
import com.binarybuffer.jlox.Expr.Visitor;
import static com.binarybuffer.jlox.Stmt.*;
import com.google.errorprone.annotations.Var;


class Interpreter implements Visitor<Object>, Stmt.Visitor<Void> {
  Environment globals = new Environment();
  private Environment environment = globals;

  Interpreter() {
    globals.define(
        "clock",
        new LoxCallable() {

          @Override
          public int arity() {
            return 0;
          }

          @Override
          public Object call(Interpreter interpreter, List<Object> args) {
            return (double) System.currentTimeMillis() / 1000.0;
          }

          @Override
          public String toString() {
            return "<native code>";
          }
        });
  }

  void interpret(Expr expression) {
    try {
      Object value = evaluate(expression);
      System.out.println(stringify(value));
    } catch (RuntimeError err) {
      Lox.runtimeError(err);
    }
  }

  void interpret(List<Stmt> stmts) {
    try {
      for (var stmt : stmts) {
        execute(stmt);
      }
    } catch (RuntimeError err) {
      Lox.runtimeError(err);
    }
  }

  void execute(Stmt stmt) {
    stmt.accept(this);
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

  @Override
  public Object visitBinaryExpr(Binary expr) {
    final var left = evaluate(expr.left);
    final var right = evaluate(expr.right);

    switch (expr.operator.type()) {
      case MINUS:
        checkNumberOperand(expr.operator, left, right);
        return (double) left - (double) right;
      case STAR:
        checkNumberOperand(expr.operator, left, right);
        return (double) left * (double) right;
      case SLASH:
        checkNumberOperand(expr.operator, left, right);
        return (double) left / (double) right;
      case EQUAL_EQUAL:
        return equals(left, right);
      case BANG_EQUAL:
        return !equals(left, right);
      case LESS:
        checkNumberOperand(expr.operator, left, right);
        return (double) left < (double) right;
      case LESS_EQUAL:
        checkNumberOperand(expr.operator, left, right);
        return (double) left <= (double) right;
      case GREATER:
        checkNumberOperand(expr.operator, left, right);
        return (double) left > (double) right;
      case GREATER_EQUAL:
        checkNumberOperand(expr.operator, left, right);
        return (double) left >= (double) right;
      case PLUS:
        if ((left instanceof String) && (right instanceof String)) {
          return (String) left + (String) right;
        } else if ((left instanceof Double) && (right instanceof Double)) {
          return (double) left + (double) right;
        }
        throw new RuntimeError(expr.operator, "Both operands needs to be either string or number");
    }

    return null;
  }

  @Override
  public Object visitGroupingExpr(Grouping expr) {
    return evaluate(expr.expression);
  }

  @Override
  public Object visitLiteralExpr(Literal expr) {
    return expr.value;
  }

  @Override
  public Object visitUnaryExpr(Unary expr) {
    final var val = evaluate(expr.right);
    switch (expr.operator.type()) {
      case MINUS:
        return -(double) val;
      case BANG:
        return !isTruthy(val);
    }

    return null;
  }

  @Override
  public Object visitConditionExpr(Condition expr) {
    if (isTruthy(expr.condition.accept(this))) {
      return expr.truthy.accept(this);
    } else if (expr.falsy != null) {
      return expr.falsy.accept(this);
    }

    return null;
  }

  private Object evaluate(Expr expr) {
    return expr.accept(this);
  }

  private boolean isTruthy(Object val) {
    if (val == null) return false;
    if (val instanceof Boolean) return (boolean) val;

    return true;
  }

  private boolean equals(Object left, Object right) {
    if (left == null && right == null) {
      return true;
    }
    if (left == null) {
      return false;
    }
    return left.equals(right);
  }

  private void checkNumberOperand(Token operator, Object... operands) {
    for (Object op : operands) {
      if (!(op instanceof Double)) {
        throw new RuntimeError(operator, "Operand(s) needs to be a number");
      }
    }
  }

  @Override
  public Void visitExpressionStmt(Expression stmt) {
    evaluate(stmt.expression);

    return null;
  }

  @Override
  public Void visitPrintStmt(Print stmt) {
    Object value = evaluate(stmt.expression);
    System.out.println(stringify(value));
    return null;
  }

  @Override
  public Void visitVarStmt(Stmt.Var stmt) {
    Object value = null;
    if (stmt.initializer != null) {
      value = stmt.initializer.accept(this);
    }

    environment.define(stmt.name.lexeme(), value);

    return null;
  }

  @Override
  public Object visitVariableExpr(Variable expr) {
    return environment.get(expr.name);
  }

  @Override
  public Object visitAssignExpr(Assign expr) {
    Object value = expr.value.accept(this);
    environment.assign(expr.name, value);

    return value;
  }

  @Override
  public Void visitBlockStmt(Block stmt) {
    var environment = new Environment(this.environment);

    return executeBlock(stmt, environment);
  }

  private Void executeBlock(Block stmt, Environment environment) {
    var prev = this.environment;
    try {
      this.environment = environment;
      for (var s : stmt.statements) {
        execute(s);
      }
    } finally {
      this.environment = prev;
    }
    return null;
  }

  @Override
  public Void visitIfStmt(If stmt) {
    if (isTruthy(stmt.condition.accept(this))) {
      stmt.thenBranch.accept(this);
    } else if (stmt.elseBranch != null) {
      stmt.elseBranch.accept(this);
    }
    return null;
  }

  @Override
  public Object visitLogicalExpr(Logical expr) {
    var left = expr.left.accept(this);
    if (expr.operator.type() == AND) {
      if (isTruthy(left)) return isTruthy(expr.right.accept(this));
    } else if (expr.operator.type() == OR) {
      if (!isTruthy(left)) return isTruthy(expr.right.accept(this));
    }

    return false;
  }

  @Override
  public Void visitWhileStmt(While stmt) {
    while (isTruthy(stmt.condition.accept(this))) {
      execute(stmt.body);
    }
    return null;
  }

  @Override
  public Object visitCallExpr(Call expr) {
    var calleeObj = evaluate(expr.callee);
    if (!(calleeObj instanceof LoxCallable)) {
      throw new RuntimeError(expr.paren, "can only call functions and classes");
    }
    var callee = (LoxCallable) calleeObj;

    List<Object> args = new ArrayList<>();
    for (var a : expr.arguments) {
      args.add(evaluate(a));
    }

    if (callee.arity() != args.size()) {
      throw new RuntimeError(
          expr.paren, "Expecting " + callee.arity() + " args but got " + args.size());
    }

    return callee.call(this, args);
  }

  @Override
  public Void visitFunctionStmt(Function stmt) {
    var f = new LoxFunction(stmt);
    environment.define(stmt.name.lexeme(), f);

    return null;
  }

  public Object executeBlock(List<Stmt> body, Environment environment) {
    var prev = this.environment;
    try {
      this.environment = environment;
      for (var s : body) {
        s.accept(this);
      }
    } finally {
      this.environment = prev;
    }

    return null;
  }

  @Override
  public Void visitReturnStmt(Stmt.Return stmt) {
    Object value = null;
    if (stmt.value != null) {
      value = stmt.value.accept(this);
    }

    throw new ReturnVal(value);

  }
}
