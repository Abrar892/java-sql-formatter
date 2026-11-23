package com.abrar.sqlfmt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SqlLexerTest {

    private List<Token> lex(String sql) {
        return new SqlLexer(sql).tokenize();
    }

    @Test
    void keywordsAreRecognisedCaseInsensitively() {
        List<Token> tokens = lex("select SeLeCt FROM where");
        assertEquals(4, tokens.size());
        assertTrue(tokens.stream().allMatch(t -> t.type() == Token.Type.KEYWORD));
        assertEquals("select", tokens.get(0).text());
        assertEquals("SeLeCt", tokens.get(1).text());
    }

    @Test
    void identifiersAndNumbersAreSeparateTokens() {
        List<Token> tokens = lex("user_id 42 3.14");
        assertEquals(Token.Type.IDENTIFIER, tokens.get(0).type());
        assertEquals("user_id", tokens.get(0).text());
        assertEquals(Token.Type.NUMBER, tokens.get(1).type());
        assertEquals(Token.Type.NUMBER, tokens.get(2).type());
        assertEquals("3.14", tokens.get(2).text());
    }

    @Test
    void stringLiteralsKeepDoubledQuoteEscapes() {
        List<Token> tokens = lex("'it''s fine'");
        assertEquals(1, tokens.size());
        assertEquals(Token.Type.STRING, tokens.get(0).type());
        assertEquals("'it''s fine'", tokens.get(0).text());
    }

    @Test
    void quotedIdentifiersSupportAllThreeStyles() {
        List<Token> tokens = lex("\"my col\" `back` [bracket]");
        assertEquals(3, tokens.size());
        assertTrue(tokens.stream().allMatch(t -> t.type() == Token.Type.QUOTED_IDENTIFIER));
        assertEquals("\"my col\"", tokens.get(0).text());
        assertEquals("`back`", tokens.get(1).text());
        assertEquals("[bracket]", tokens.get(2).text());
    }

    @Test
    void lineAndBlockCommentsAreTokens() {
        List<Token> tokens = lex("a -- trailing\n/* multi\nline */ b");
        assertEquals(Token.Type.IDENTIFIER, tokens.get(0).type());
        assertEquals(Token.Type.LINE_COMMENT, tokens.get(1).type());
        assertEquals("-- trailing", tokens.get(1).text());
        assertEquals(Token.Type.BLOCK_COMMENT, tokens.get(2).type());
        assertEquals(Token.Type.IDENTIFIER, tokens.get(3).type());
    }

    @Test
    void placeholdersCoverQuestionMarkNamedAndPositional() {
        List<Token> tokens = lex("? :name $1 @param");
        assertEquals(4, tokens.size());
        assertTrue(tokens.stream().allMatch(t -> t.type() == Token.Type.PLACEHOLDER));
        assertEquals("?", tokens.get(0).text());
        assertEquals(":name", tokens.get(1).text());
        assertEquals("$1", tokens.get(2).text());
        assertEquals("@param", tokens.get(3).text());
    }

    @Test
    void multiCharacterOperatorsAreSingleTokens() {
        List<Token> tokens = lex("a <= b <> c != d || e :: f");
        List<String> ops = tokens.stream()
                .filter(t -> t.type() == Token.Type.OPERATOR)
                .map(Token::text)
                .toList();
        assertEquals(List.of("<=", "<>", "!=", "||", "::"), ops);
    }

    @Test
    void punctuationIsTokenised() {
        List<Token> tokens = lex("f(a, b.c);");
        List<Token.Type> types = tokens.stream().map(Token::type).toList();
        assertEquals(List.of(
                Token.Type.IDENTIFIER, Token.Type.OPEN_PAREN, Token.Type.IDENTIFIER,
                Token.Type.COMMA, Token.Type.IDENTIFIER, Token.Type.DOT,
                Token.Type.IDENTIFIER, Token.Type.CLOSE_PAREN, Token.Type.SEMICOLON),
                types);
    }

    @Test
    void whitespaceOnlyInputProducesNoTokens() {
        assertTrue(lex("  \n\t ").isEmpty());
    }

    @Test
    void exponentNumbersAreSingleTokens() {
        List<Token> tokens = lex("1.5e-3 2E10");
        assertEquals(2, tokens.size());
        assertEquals("1.5e-3", tokens.get(0).text());
        assertEquals("2E10", tokens.get(1).text());
    }
}
