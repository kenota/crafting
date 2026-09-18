package com.binarybuffer.jlox;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.binarybuffer.jlox.TokenType.*;

public class ScannerTest {
    @Test
    void shouldParseMultilineComment() {
        String input = "/* \n - */ + /* some more text + / */";
        Scanner scanner = new Scanner(input);

        Token[] expected = new Token[]{new Token(PLUS, "+", null, 2), new Token(EOF, "", null, 2)};
        assertArrayEquals(expected, scanner.scanTokens().toArray());

    }
}
