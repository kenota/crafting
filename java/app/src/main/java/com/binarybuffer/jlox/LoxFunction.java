package com.binarybuffer.jlox;

import java.util.List;

class LoxFunction implements LoxCallable {
  private final Stmt.Function declaration;

  public LoxFunction(Stmt.Function declaration) {
    this.declaration = declaration;
  }

	@Override
	public int arity() {
	  return declaration.params.size();
	}

	@Override
	public Object call(Interpreter interpreter, List<Object> args) {
	  Environment environment = new Environment(interpreter.globals);
		for (int i = 0; i < args.size(); i++) {
		  environment.define(this.declaration.params.get(i).lexeme(), args.get(i));
		}

		try {
		  interpreter.executeBlock(declaration.body, environment);
		} catch (ReturnVal ex) {
		  return ex.value;
		}

		return null;
	}

	@Override
	public String toString() {
	  return "<fn " + this.declaration.name.lexeme() + ">";
	}

}
