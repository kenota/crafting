package com.binarybuffer.jlox;

import com.binarybuffer.jlox.Expr.Assign;
import com.binarybuffer.jlox.Expr.Binary;
import com.binarybuffer.jlox.Expr.Condition;
import com.binarybuffer.jlox.Expr.Grouping;
import com.binarybuffer.jlox.Expr.Literal;
import com.binarybuffer.jlox.Expr.Unary;
import com.binarybuffer.jlox.Expr.Variable;
import com.binarybuffer.jlox.Expr.Visitor;
import com.binarybuffer.jlox.Stmt.Block;
import com.binarybuffer.jlox.Stmt.Expression;
import com.binarybuffer.jlox.Stmt.Print;
import com.binarybuffer.jlox.Stmt.Var;

import static com.binarybuffer.jlox.Token.*;

import java.util.List;

class Interpreter implements Visitor<Object>, Stmt.Visitor<Void> {
    private Environment environment = new Environment();

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
            for (var stmt: stmts) {
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
		    return (double)left - (double) right;
		case STAR:
		    checkNumberOperand(expr.operator, left, right);
		    return (double)left * (double) right;
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
		    if  ((left instanceof String) && (right instanceof String)) {
				return (String)left + (String) right;
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
		    return -(double)val;
		case BANG:
		    return !isTruthy(val);
		}

		return null;
	}

	@Override
	public Object visitConditionExpr(Condition expr) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'visitConditionExpr'");
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
	public Void visitVarStmt(Var stmt) {
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
			for (var s: stmt.statements) {
			    execute(s);
			}
		} finally {
		    this.environment = prev;
		}
		return null;
	}

}
