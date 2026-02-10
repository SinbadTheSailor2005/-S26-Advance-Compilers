package org.stella.typecheck;

import org.stella.typecheck.exceptions.TypeCheckException;
import org.syntax.stella.Absyn.*;
import org.stella.typecheck.VisitTypeCheck;

public class TypeCheck
{
    public static void typecheckProgram(Program program) throws Exception
    {

            VisitTypeCheck v = new VisitTypeCheck();
            program.accept(v.new ProgramVisitor(), new Context() /* initial context
            information*/);

    }
}
