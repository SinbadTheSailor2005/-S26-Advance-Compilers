package org.stella;

import java.io.*;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.*;
import java.util.*;

import org.stella.typecheck.TypeCheck;
import org.stella.typecheck.exceptions.TypeCheckException;
import org.syntax.stella.*;
import org.syntax.stella.Absyn.*;
import org.stella.eval.*;

public class Main
{
    stellaLexer l;
    stellaParser p;

    // Конструктор принимает InputStream, чтобы можно было подсунуть файл в тестах
    public Main(InputStream input) throws IOException {
        Reader reader = new InputStreamReader(input);
        l = new stellaLexer(new ANTLRInputStream(reader));
        l.addErrorListener(new BNFCErrorListener());
        p = new stellaParser(new CommonTokenStream(l));
        p.addErrorListener(new BNFCErrorListener());
    }

    public Program parse() throws Exception {
        stellaParser.Start_ProgramContext pc = p.start_Program();
        return pc.result;
    }

    // Точка входа для реального запуска
    public static void main(String args[]) throws Exception {
        // Вызываем compile c реальным System.in
        int exitCode = compile(System.in, System.out, System.err);
        System.exit(exitCode);
    }

    // Метод для тестов: возвращает int вместо System.exit
    public static int compile(InputStream in, PrintStream out, PrintStream err) throws Exception {
        try {
            Main t = new Main(in);
            Program ast = t.parse();
            TypeCheck.typecheckProgram(ast);
            return 0; // Успех
        } catch (TypeCheckException e) {
            err.println(e.getMessage());
            return 1; // Ошибка типов
        } catch (TestError e) {
            err.println("Syntax Error: " + e.getMessage());
            return 1; // Ошибка синтаксиса
        } catch (Exception e) {
            err.println("Internal Error: " + e.getMessage());
            e.printStackTrace(err);
            return 1; // Другая ошибка
        }
    }
}

class TestError extends RuntimeException
{
    int line;
    int column;
    public TestError(String msg, int l, int c)
    {
        super(msg);
        line = l;
        column = c;
    }
}

class BNFCErrorListener implements ANTLRErrorListener
{
    @Override
    public void syntaxError(Recognizer<?, ?> recognizer, Object o, int i, int i1, String s, RecognitionException e)
    {
        throw new TestError(s,i,i1);
    }
    @Override
    public void reportAmbiguity(Parser parser, DFA dfa, int i, int i1, boolean b, BitSet bitSet, ATNConfigSet atnConfigSet)
    {
        throw new TestError("Ambiguity at",i,i1);
    }
    @Override
    public void reportAttemptingFullContext(Parser parser, DFA dfa, int i, int i1, BitSet bitSet, ATNConfigSet atnConfigSet)
    {
    }
    @Override
    public void reportContextSensitivity(Parser parser, DFA dfa, int i, int i1, int i2, ATNConfigSet atnConfigSet)
    {
    }
}