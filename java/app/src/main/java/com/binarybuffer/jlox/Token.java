package com.binarybuffer.jlox;

record Token(TokenType type, String lexeme, Object literal, int line) {};
