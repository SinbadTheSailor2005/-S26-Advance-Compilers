package org.stella.typecheck;

import org.stella.typecheck.exceptions.TypeCheckException;
import org.syntax.stella.Absyn.*;
import org.stella.typecheck.VisitTypeCheck;

public class TypeCheck
{
    public static void typecheckProgram(Program program) throws Exception
    {
        try {

            VisitTypeCheck v = new VisitTypeCheck();
            program.accept(v.new ProgramVisitor(), new Context() /* initial context
            information*/);
            System.exit(0);
        } catch (TypeCheckException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }
}
