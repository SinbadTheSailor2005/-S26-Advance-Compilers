

package org.stella.typecheck;

import org.stella.typecheck.exceptions.TypeCheckException;
import org.syntax.stella.Absyn.*;

import java.util.Optional;

/*** Visitor Design Pattern Skeleton. ***/

/* This implements the common visitor design pattern.
   Tests show it to be slightly less efficient than the
   instanceof method, but easier to use.
   Replace the R and A parameters with the desired return
   and context types.*/

public class VisitTypeCheck {

  public static boolean isSameType(Type t1, Type t2) {
    if (t1 == null || t2 == null) return false;
    if (t1 instanceof TypeAuto || t2 instanceof TypeAuto) return true;
    if (t1 instanceof TypeBottom || t2 instanceof TypeBottom) return true;

    if (t1 instanceof TypeNat && t2 instanceof TypeNat) return true;
    if (t1 instanceof TypeBool && t2 instanceof TypeBool) return true;
    if (t1 instanceof TypeUnit && t2 instanceof TypeUnit) return true;


    if (t1 instanceof TypeFun tf1 && t2 instanceof TypeFun tf2) {
      return isSameType(
              tf1.listtype_.get(0), tf2.listtype_.get(0)) && isSameType(
              tf1.type_, tf2.type_);
    }


    if (t1 instanceof TypeList tl1 && t2 instanceof TypeList tl2) {
      return isSameType(tl1.type_, tl2.type_);
    }


    if (t1 instanceof TypeTuple tt1 && t2 instanceof TypeTuple tt2) {
      if (tt1.listtype_.size() != tt2.listtype_.size()) return false;
      for (int i = 0; i < tt1.listtype_.size(); i++) {
        if (!isSameType(tt1.listtype_.get(i), tt2.listtype_.get(i)))
          return false;
      }
      return true;
    }


    if (t1 instanceof TypeSum ts1 && t2 instanceof TypeSum ts2) {
      return isSameType(ts1.type_1, ts2.type_1) && isSameType(
              ts1.type_2, ts2.type_2);
    }


    if (t1 instanceof TypeRecord tr1 && t2 instanceof TypeRecord tr2) {
      if (tr1.listrecordfieldtype_.size() != tr2.listrecordfieldtype_.size())
        return false;

      for (org.syntax.stella.Absyn.RecordFieldType f1_abstract : tr1.listrecordfieldtype_) {
        org.syntax.stella.Absyn.ARecordFieldType f1 =
                (org.syntax.stella.Absyn.ARecordFieldType) f1_abstract;

        boolean found = false;

        for (org.syntax.stella.Absyn.RecordFieldType f2_abstract : tr2.listrecordfieldtype_) {
          org.syntax.stella.Absyn.ARecordFieldType f2 =
                  (org.syntax.stella.Absyn.ARecordFieldType) f2_abstract;

          if (f1.stellaident_.equals(f2.stellaident_)) {
            if (!isSameType(f1.type_, f2.type_)) {
              return false;
            }
            found = true;
            break;
          }
        }

        if (!found) return false;
      }

      return true;
    }

    return false;
  }

  public class ProgramVisitor implements org.syntax.stella.Absyn.Program.Visitor<Type, Context> {
    public Type visit(
            org.syntax.stella.Absyn.AProgram p,
            Context ctx) { /* Code for AProgram goes here */
      p.languagedecl_.accept(new LanguageDeclVisitor(), ctx);
      for (org.syntax.stella.Absyn.Extension x : p.listextension_) {
        x.accept(new ExtensionVisitor(), ctx);
      }
      checkMain(p.listdecl_);
      ctx.enterScope();
      for (org.syntax.stella.Absyn.Decl x : p.listdecl_) {
        if (x instanceof DeclFun df) {
          //TODO: check correctness: у нас сразу функции видны? или только те
          // что сверху?
          Type argType = ((AParamDecl) df.listparamdecl_.getFirst()).type_;
          Type returnType =
                  (df.returntype_ instanceof SomeReturnType srt) ? srt.type_ : null;
          ListType lt = new ListType();
          lt.add(argType);
          ctx.addVariable(df.stellaident_, new TypeFun(lt, returnType));
        }
      }


      for (Decl x : p.listdecl_) {
        x.accept(new DeclVisitor(), ctx);
      }
      return null;
    }

    private void checkMain(ListDecl listdecl) {
      var declType = checkMainExistence(listdecl);
      checkMainType(declType);
    }

    private void checkMainType(Decl declType) {
      if (!(declType instanceof DeclFun)) {
        throw new TypeCheckException(
                TypeCheckException.ErrorType.ERROR_INCORRECT_TYPE_OF_MAIN,
                "Function 'main' has incorrect type. Should be function"
        );
      }
    }

    private Decl checkMainExistence(ListDecl listdecl) {
      for (var decl : listdecl) {
        if (decl instanceof DeclFun df) {
          String name = df.stellaident_.trim();
          if (name.equals("main")) {
            return decl;
          }
        }

      }
      throw new TypeCheckException(
              TypeCheckException.ErrorType.ERROR_MISSING_MAIN,
              "Function 'main' is not defined."
      );
    }

    public static class LanguageDeclVisitor implements org.syntax.stella.Absyn.LanguageDecl.Visitor<Type, Context> {
      public Type visit(
              org.syntax.stella.Absyn.LanguageCore p,
              Context ctx) { /* Code for LanguageCore goes here */
        return null;
      }
    }

    public static class ExtensionVisitor implements org.syntax.stella.Absyn.Extension.Visitor<Type, Context> {
      public Type visit(
              org.syntax.stella.Absyn.AnExtension p,
              Context ctx) { /* Code for AnExtension goes here */
        for (String x : p.listextensionname_) {

        }
        return null;
      }
    }

    public static void checkForMismatch(
            org.syntax.stella.Absyn.Type expected,
            org.syntax.stella.Absyn.Type actual) {
      if (isSameType(expected, actual)) return;
      if (expected instanceof org.syntax.stella.Absyn.TypeRecord expectedRecord && actual instanceof org.syntax.stella.Absyn.TypeRecord actualRecord) {
        checkRecordMismatch(expectedRecord, actualRecord);
      }


      throw new TypeCheckException(
              TypeCheckException.ErrorType.ERROR_UNEXPECTED_TYPE_FOR_EXPRESSION,
              expected, actual
      );
    }


    public static void checkRecordMismatch(
            org.syntax.stella.Absyn.TypeRecord expected,
            org.syntax.stella.Absyn.TypeRecord actual) {

      // 1. Собираем имена полей в множества (Set) для удобства
      java.util.Set<String> expectedFields = new java.util.HashSet<>();
      for (org.syntax.stella.Absyn.RecordFieldType f : expected.listrecordfieldtype_) {
        expectedFields.add(
                ((org.syntax.stella.Absyn.ARecordFieldType) f).stellaident_);
      }

      java.util.Set<String> actualFields = new java.util.HashSet<>();
      for (org.syntax.stella.Absyn.RecordFieldType f : actual.listrecordfieldtype_) {
        actualFields.add(
                ((org.syntax.stella.Absyn.ARecordFieldType) f).stellaident_);
      }

      // 2. Проверяем MISSING (Чего не хватает в actual, но есть в expected)
      java.util.List<String> missing = new java.util.ArrayList<>();
      for (String f : expectedFields) {
        if (!actualFields.contains(f)) {
          missing.add(f);
        }
      }

      if (!missing.isEmpty()) {
        throw new TypeCheckException(
                TypeCheckException.ErrorType.ERROR_MISSING_RECORD_FIELDS,
                "Record is missing required fields: " + String.join(
                        ", ",
                        missing
                )
        );
      }

      // 3. Проверяем UNEXPECTED (Что есть в actual, но нет в expected)
      java.util.List<String> unexpected = new java.util.ArrayList<>();
      for (String f : actualFields) {
        if (!expectedFields.contains(f)) {
          unexpected.add(f);
        }
      }

      if (!unexpected.isEmpty()) {
        throw new TypeCheckException(
                TypeCheckException.ErrorType.ERROR_UNEXPECTED_RECORD_FIELDS,
                "Record contains unexpected fields: " + String.join(
                        ", ",
                        unexpected
                )
        );
      }

      // Если поля (ключи) совпали, значит проблема в типах значений полей.
      // Это уже ловит обычный isSameType или рекурсивный обход.
    }

    public class DeclVisitor implements org.syntax.stella.Absyn.Decl.Visitor<Type, Context> {
      public Type visit(org.syntax.stella.Absyn.DeclFun p, Context ctx) {
        ctx.enterScope();

        for (org.syntax.stella.Absyn.ParamDecl x : p.listparamdecl_) {
          if (x instanceof org.syntax.stella.Absyn.AParamDecl ap) {
            ctx.addVariable(ap.stellaident_, ap.type_);
          }
        }

        for (org.syntax.stella.Absyn.Decl decl : p.listdecl_) {
          if (decl instanceof DeclFun df) {
            Type argType = ((AParamDecl) df.listparamdecl_.getFirst()).type_;

            Type retType =
                    (df.returntype_ instanceof SomeReturnType srt) ? srt.type_ : new TypeUnit();

            ListType paramTypes = new ListType();
            paramTypes.add(argType);
            TypeFun funType = new TypeFun(paramTypes, retType);

            ctx.addVariable(df.stellaident_, funType);
          }
        }

        for (org.syntax.stella.Absyn.Decl decl : p.listdecl_) {
          decl.accept(this, ctx);
        }

        Type expectedRetType =
                p.returntype_.accept(new ReturnTypeVisitor(), ctx);
        ctx.expectedType = expectedRetType;
        Type actualRetType = p.expr_.accept(new ExprVisitor(), ctx);
        ctx.expectedType = null;
        checkForMismatch(expectedRetType, actualRetType);


        ctx.exitScope();

        return actualRetType;
      }


      public Type visit(
              org.syntax.stella.Absyn.DeclFunGeneric p,
              Context ctx) { /* Code for DeclFunGeneric goes here */
        for (org.syntax.stella.Absyn.Annotation x : p.listannotation_) {
          x.accept(new AnnotationVisitor(), ctx);
        }

        for (String x : p.liststellaident_) {

        }
        for (org.syntax.stella.Absyn.ParamDecl x : p.listparamdecl_) {
          x.accept(new ParamDeclVisitor(), ctx);
        }
        p.returntype_.accept(new ReturnTypeVisitor(), ctx);
        p.throwtype_.accept(new ThrowTypeVisitor(), ctx);
        for (org.syntax.stella.Absyn.Decl x : p.listdecl_) {
          x.accept(new DeclVisitor(), ctx);
        }
        p.expr_.accept(new ExprVisitor(), ctx);
        return null;
      }

      public Type visit(
              org.syntax.stella.Absyn.DeclTypeAlias p,
              Context ctx) { /* Code for DeclTypeAlias goes here */

        p.type_.accept(new TypeVisitor(), ctx);
        return null;
      }

      public Type visit(
              org.syntax.stella.Absyn.DeclExceptionType p,
              Context ctx) { /* Code for DeclExceptionType goes here */
        p.type_.accept(new TypeVisitor(), ctx);
        return null;
      }

      public Type visit(
              org.syntax.stella.Absyn.DeclExceptionVariant p,
              Context ctx) { /* Code for DeclExceptionVariant goes here */

        p.type_.accept(new TypeVisitor(), ctx);
        return null;
      }
    }

    public class LocalDeclVisitor implements org.syntax.stella.Absyn.LocalDecl.Visitor<Type, Context> {
      public Type visit(
              org.syntax.stella.Absyn.ALocalDecl p,
              Context ctx) { /* Code for ALocalDecl goes here */
        p.decl_.accept(new DeclVisitor(), ctx);
        return null;
      }
    }

    public static class AnnotationVisitor implements org.syntax.stella.Absyn.Annotation.Visitor<Type, Context> {
      public Type visit(
              org.syntax.stella.Absyn.InlineAnnotation p,
              Context ctx) { /* Code for InlineAnnotation goes here */
        return null;
      }
    }

    public class ParamDeclVisitor implements org.syntax.stella.Absyn.ParamDecl.Visitor<Type, Context> {
      public Type visit(
              org.syntax.stella.Absyn.AParamDecl p,
              Context ctx) { /* Code for AParamDecl goes here */

        return p.type_.accept(new TypeVisitor(), ctx);
      }
    }

    public class ReturnTypeVisitor implements org.syntax.stella.Absyn.ReturnType.Visitor<Type, Context> {
      public Type visit(
              org.syntax.stella.Absyn.NoReturnType p,
              Context ctx) { /* Code for NoReturnType goes here */
        return new TypeUnit();
      }

      public Type visit(
              org.syntax.stella.Absyn.SomeReturnType p,
              Context ctx) { /* Code for SomeReturnType goes here */
        return p.type_.accept(new TypeVisitor(), ctx);
      }
    }

    public class ThrowTypeVisitor implements org.syntax.stella.Absyn.ThrowType.Visitor<Type, Context> {
      public Type visit(
              org.syntax.stella.Absyn.NoThrowType p,
              Context ctx) { /* Code for NoThrowType goes here */
        return null;
      }

      public Type visit(
              org.syntax.stella.Absyn.SomeThrowType p,
              Context ctx) { /* Code for SomeThrowType goes here */
        for (org.syntax.stella.Absyn.Type x : p.listtype_) {
          x.accept(new TypeVisitor(), ctx);
        }
        return null;
      }
    }

    public class TypeVisitor implements org.syntax.stella.Absyn.Type.Visitor<Type, Context> {
      @Override
      public Type visit(org.syntax.stella.Absyn.TypeRecord p, Context ctx) {
        java.util.Set<String> fields = new java.util.HashSet<>();
        for (org.syntax.stella.Absyn.RecordFieldType f : p.listrecordfieldtype_) {
          org.syntax.stella.Absyn.ARecordFieldType field =
                  (org.syntax.stella.Absyn.ARecordFieldType) f;

          if (fields.contains(field.stellaident_)) {
            throw new TypeCheckException(
                    TypeCheckException.ErrorType.ERROR_DUPLICATE_RECORD_TYPE_FIELDS,
                    "Duplicate field '" + field.stellaident_ + "' in record " + p
            );
          }
          fields.add(field.stellaident_);

          field.type_.accept(this, ctx);
        }
        return p;
      }
      // Метод для детального сравнения записей и выброса специфичных ошибок

      @Override
      public Type visit(org.syntax.stella.Absyn.TypeVariant p, Context ctx) {
        java.util.Set<String> labels = new java.util.HashSet<>();
        for (org.syntax.stella.Absyn.VariantFieldType f : p.listvariantfieldtype_) {
          org.syntax.stella.Absyn.AVariantFieldType field =
                  (org.syntax.stella.Absyn.AVariantFieldType) f;

          if (labels.contains(field.stellaident_)) {
            throw new TypeCheckException(
                    TypeCheckException.ErrorType.ERROR_DUPLICATE_VARIANT_TYPE_FIELDS,
                    "Duplicate label '" + field.stellaident_ + "'"
            );
          }
          labels.add(field.stellaident_);

          field.optionaltyping_.accept(new OptionalTypingVisitor(), ctx); //
          // TODO: что это?
        }
        return p;
      }

      // TODO: Diferrence TypeRec vs TypeRecord
      public Type visit(org.syntax.stella.Absyn.TypeRec p, Context ctx) {
        return p;
      }

      public Type visit(org.syntax.stella.Absyn.TypeFun p, Context ctx) {
        // assumed that 1 param only
        for (org.syntax.stella.Absyn.Type paramType : p.listtype_)
          paramType.accept(this, ctx);
        p.type_.accept(this, ctx);
        return p;
      }

      public Type visit(org.syntax.stella.Absyn.TypeTuple p, Context ctx) {
        for (org.syntax.stella.Absyn.Type t : p.listtype_) t.accept(this, ctx);
        return p;
      }

      public Type visit(org.syntax.stella.Absyn.TypeSum p, Context ctx) {
        p.type_1.accept(this, ctx);
        p.type_2.accept(this, ctx);
        return p;
      }

      public Type visit(org.syntax.stella.Absyn.TypeList p, Context ctx) {
        p.type_.accept(this, ctx);
        return p;
      }

      // 4. ПРОСТЫЕ ТИПЫ (Возврат себя)
      public Type visit(org.syntax.stella.Absyn.TypeNat p, Context ctx) {
        return p;
      }

      public Type visit(org.syntax.stella.Absyn.TypeBool p, Context ctx) {
        return p;
      }

      public Type visit(org.syntax.stella.Absyn.TypeUnit p, Context ctx) {
        return p;
      }

      public Type visit(org.syntax.stella.Absyn.TypeAuto p, Context ctx) {
        return p;
      } // Обычно выводится само

      // Остальные можно оставить return p;
      public Type visit(org.syntax.stella.Absyn.TypeForAll p, Context ctx) {
        return p;
      }


      public Type visit(org.syntax.stella.Absyn.TypeTop p, Context ctx) {
        return p;
      }

      public Type visit(org.syntax.stella.Absyn.TypeBottom p, Context ctx) {
        return p;
      }

      public Type visit(org.syntax.stella.Absyn.TypeRef p, Context ctx) {
        return p;
      }

      public Type visit(org.syntax.stella.Absyn.TypeVar p, Context ctx) {
        return p;
      }
    }

    public class MatchCaseVisitor implements org.syntax.stella.Absyn.MatchCase.Visitor<Type, Context> {
      public Type visit(
              org.syntax.stella.Absyn.AMatchCase p,
              Context ctx) { /* Code for AMatchCase goes here */
        p.pattern_.accept(new PatternVisitor(), ctx);
        p.expr_.accept(new ExprVisitor(), ctx);
        return null;
      }
    }

    public class OptionalTypingVisitor implements org.syntax.stella.Absyn.OptionalTyping.Visitor<Type, Context> {
      public Type visit(
              org.syntax.stella.Absyn.NoTyping p,
              Context ctx) { /* Code for NoTyping goes here */
        return null;
      }

      public Type visit(
              org.syntax.stella.Absyn.SomeTyping p,
              Context ctx) { /* Code for SomeTyping goes here */
        p.type_.accept(new TypeVisitor(), ctx);
        return null;
      }
    }

    public class PatternDataVisitor implements org.syntax.stella.Absyn.PatternData.Visitor<Type, Context> {
      public Type visit(
              org.syntax.stella.Absyn.NoPatternData p,
              Context ctx) { /* Code for NoPatternData goes here */
        return null;
      }

      public Type visit(
              org.syntax.stella.Absyn.SomePatternData p,
              Context ctx) { /* Code for SomePatternData goes here */
        p.pattern_.accept(new PatternVisitor(), ctx);
        return null;
      }
    }

    public class ExprDataVisitor implements org.syntax.stella.Absyn.ExprData.Visitor<Type, Context> {
      public Type visit(
              org.syntax.stella.Absyn.NoExprData p,
              Context ctx) { /* Code for NoExprData goes here */
        return null;
      }

      public Type visit(
              org.syntax.stella.Absyn.SomeExprData p,
              Context ctx) { /* Code for SomeExprData goes here */
        p.expr_.accept(new ExprVisitor(), ctx);
        return null;
      }
    }

    public class PatternVisitor implements org.syntax.stella.Absyn.Pattern.Visitor<Type, Context> {
      public Type visit(
              org.syntax.stella.Absyn.PatternCastAs p,
              Context ctx) { /* Code for PatternCastAs goes here */
        p.pattern_.accept(new PatternVisitor(), ctx);
        p.type_.accept(new TypeVisitor(), ctx);
        return null;
      }

      public Type visit(
              org.syntax.stella.Absyn.PatternAsc p,
              Context ctx) { /* Code for PatternAsc goes here */
        p.pattern_.accept(new PatternVisitor(), ctx);
        p.type_.accept(new TypeVisitor(), ctx);
        return null;
      }

      public Type visit(
              org.syntax.stella.Absyn.PatternVariant p,
              Context ctx) { /* Code for PatternVariant goes here */

        p.patterndata_.accept(new PatternDataVisitor(), ctx);
        return null;
      }

      public Type visit(
              org.syntax.stella.Absyn.PatternInl p,
              Context ctx) { /* Code for PatternInl goes here */
        return p.pattern_.accept(new PatternVisitor(), ctx);
      }

      public Type visit(
              org.syntax.stella.Absyn.PatternInr p,
              Context ctx) { /* Code for PatternInr goes here */
        p.pattern_.accept(new PatternVisitor(), ctx);
        return null;
      }

      public Type visit(
              org.syntax.stella.Absyn.PatternTuple p,
              Context ctx) { /* Code for PatternTuple goes here */
        for (org.syntax.stella.Absyn.Pattern x : p.listpattern_) {
          x.accept(new PatternVisitor(), ctx);
        }
        return null;
      }

      public Type visit(
              org.syntax.stella.Absyn.PatternRecord p,
              Context ctx) { /* Code for PatternRecord goes here */
        for (org.syntax.stella.Absyn.LabelledPattern x : p.listlabelledpattern_) {
          x.accept(new LabelledPatternVisitor(), ctx);
        }
        return null;
      }

      public Type visit(
              org.syntax.stella.Absyn.PatternList p,
              Context ctx) { /* Code for PatternList goes here */
        for (org.syntax.stella.Absyn.Pattern x : p.listpattern_) {
          x.accept(new PatternVisitor(), ctx);
        }
        return null;
      }

      public Type visit(
              org.syntax.stella.Absyn.PatternCons p,
              Context ctx) { /* Code for PatternCons goes here */
        p.pattern_1.accept(new PatternVisitor(), ctx);
        p.pattern_2.accept(new PatternVisitor(), ctx);
        return null;
      }

      public Type visit(
              org.syntax.stella.Absyn.PatternFalse p,
              Context ctx) { /* Code for PatternFalse goes here */
        return null;
      }

      public Type visit(
              org.syntax.stella.Absyn.PatternTrue p,
              Context ctx) { /* Code for PatternTrue goes here */
        return null;
      }

      public Type visit(
              org.syntax.stella.Absyn.PatternUnit p,
              Context ctx) { /* Code for PatternUnit goes here */
        return null;
      }

      public Type visit(
              org.syntax.stella.Absyn.PatternInt p,
              Context ctx) { /* Code for PatternInt goes here */

        return null;
      }

      public Type visit(
              org.syntax.stella.Absyn.PatternSucc p,
              Context ctx) { /* Code for PatternSucc goes here */
        p.pattern_.accept(new PatternVisitor(), ctx);
        return null;
      }

      public Type visit(
              org.syntax.stella.Absyn.PatternVar p,
              Context ctx) { /* Code for PatternVar goes here */

        return null;
      }
    }

    public class LabelledPatternVisitor implements org.syntax.stella.Absyn.LabelledPattern.Visitor<Type, Context> {
      public Type visit(
              org.syntax.stella.Absyn.ALabelledPattern p,
              Context ctx) { /* Code for ALabelledPattern goes here */

        p.pattern_.accept(new PatternVisitor(), ctx);
        return null;
      }
    }

    public class BindingVisitor implements org.syntax.stella.Absyn.Binding.Visitor<Type, Context> {
      public Type visit(
              org.syntax.stella.Absyn.ABinding p,
              Context ctx) { /* Code for ABinding goes here */

        return p.expr_.accept(new ExprVisitor(), ctx);
      }
    }

    public class ExprVisitor implements org.syntax.stella.Absyn.Expr.Visitor<Type, Context> {
      public Type visit(
              org.syntax.stella.Absyn.Sequence p,
              Context ctx) { /* Code for Sequence goes here */
        p.expr_1.accept(new ExprVisitor(), ctx);
        p.expr_2.accept(new ExprVisitor(), ctx);
        return null;
      }

      public Type visit(
              org.syntax.stella.Absyn.Let p,
              Context ctx) { /* Code for Let goes here */
        for (org.syntax.stella.Absyn.PatternBinding x : p.listpatternbinding_) {
          x.accept(new PatternBindingVisitor(), ctx);
        }
        p.expr_.accept(new ExprVisitor(), ctx);
        return null;
      }

      // TODO: нужно?
      public Type visit(
              org.syntax.stella.Absyn.LetRec p,
              Context ctx) { /* Code for LetRec goes here */
        for (org.syntax.stella.Absyn.PatternBinding x : p.listpatternbinding_) {
          x.accept(new PatternBindingVisitor(), ctx);
        }
        p.expr_.accept(new ExprVisitor(), ctx);
        return null;
      }

      // its fot Genretics
      public Type visit(
              org.syntax.stella.Absyn.TypeAbstraction p,
              Context ctx) { /* Code for TypeAbstraction goes here */
        for (String x : p.liststellaident_) {

        }
        p.expr_.accept(new ExprVisitor(), ctx);
        return null;
      }

      public Type visit(
              org.syntax.stella.Absyn.Assign p,
              Context ctx) { /* Code for Assign goes here */
        p.expr_1.accept(new ExprVisitor(), ctx);
        p.expr_2.accept(new ExprVisitor(), ctx);
        return null;
      }

      public Type visit(
              org.syntax.stella.Absyn.If p,
              Context ctx) { /* Code for If goes here */
        var t1 = p.expr_1.accept(new ExprVisitor(), ctx);
        var t2 = p.expr_2.accept(new ExprVisitor(), ctx);
        var t3 = p.expr_3.accept(new ExprVisitor(), ctx);
        if (!(t1 instanceof TypeBool)) {
          throw new TypeCheckException(
                  TypeCheckException.ErrorType.ERROR_UNEXPECTED_TYPE_FOR_EXPRESSION,
                  new TypeBool(), t1
          );
        }
        if (!(isSameType(t2, t3))) {
          throw new TypeCheckException(
                  TypeCheckException.ErrorType.ERROR_UNEXPECTED_TYPE_FOR_EXPRESSION,
                  t2, t3
          );

        }
        return t2;
      }

      public Type visit(
              org.syntax.stella.Absyn.LessThan p,
              Context ctx) { /* Code for LessThan goes here */
        p.expr_1.accept(new ExprVisitor(), ctx);
        p.expr_2.accept(new ExprVisitor(), ctx);
        return null;
      }

      public Type visit(
              org.syntax.stella.Absyn.LessThanOrEqual p,
              Context ctx) { /* Code for LessThanOrEqual goes here */
        p.expr_1.accept(new ExprVisitor(), ctx);
        p.expr_2.accept(new ExprVisitor(), ctx);
        return null;
      }

      public Type visit(
              org.syntax.stella.Absyn.GreaterThan p,
              Context ctx) { /* Code for GreaterThan goes here */
        p.expr_1.accept(new ExprVisitor(), ctx);
        p.expr_2.accept(new ExprVisitor(), ctx);
        return null;
      }

      public Type visit(
              org.syntax.stella.Absyn.GreaterThanOrEqual p,
              Context ctx) { /* Code for GreaterThanOrEqual goes here */
        p.expr_1.accept(new ExprVisitor(), ctx);
        p.expr_2.accept(new ExprVisitor(), ctx);
        return null;
      }

      public Type visit(
              org.syntax.stella.Absyn.Equal p,
              Context ctx) { /* Code for Equal goes here */
        p.expr_1.accept(new ExprVisitor(), ctx);
        p.expr_2.accept(new ExprVisitor(), ctx);
        return null;
      }

      public Type visit(
              org.syntax.stella.Absyn.NotEqual p,
              Context ctx) { /* Code for NotEqual goes here */
        p.expr_1.accept(new ExprVisitor(), ctx);
        p.expr_2.accept(new ExprVisitor(), ctx);
        return null;
      }

      public Type visit(
              org.syntax.stella.Absyn.TypeAsc p,
              Context ctx) { /* Code for TypeAsc goes here */
        var expectedType = p.type_.accept(new TypeVisitor(), ctx);
        Type oldExpectedType = ctx.expectedType;
        ctx.expectedType = expectedType;
        var actualType = p.expr_.accept(new ExprVisitor(), ctx);
        ctx.expectedType = oldExpectedType;
        checkForMismatch(expectedType, actualType);
        return p.type_;
      }

      public Type visit(
              org.syntax.stella.Absyn.TypeCast p,
              Context ctx) { /* Code for TypeCast goes here */
        p.expr_.accept(new ExprVisitor(), ctx);
        p.type_.accept(new TypeVisitor(), ctx);
        return null;
      }

      /**
       * @param p   - Abstraction expression with One parameter
       * @param ctx
       * @return TODO: чекнуть работу
       */
      public Type visit(
              org.syntax.stella.Absyn.Abstraction p,
              Context ctx) { /* Code for Abstraction goes here */


        AParamDecl param = (AParamDecl) p.listparamdecl_.getFirst();
        ctx.enterScope();
        ctx.addVariable(param.stellaident_, param.type_);
        Type returnType = p.expr_.accept(new ExprVisitor(), ctx);
        // TODO:Нужно?
        param.type_.accept(new TypeVisitor(), ctx);
        returnType.accept(new TypeVisitor(), ctx);

        ctx.exitScope();
        ListType types = new ListType();
        types.add(param.type_);
        return new TypeFun(types, returnType);
      }

      public Type visit(
              org.syntax.stella.Absyn.Variant p,
              Context ctx) { /* Code for Variant goes here */

        p.exprdata_.accept(new ExprDataVisitor(), ctx);
        return null;
      }

      /**
       * pattern должен совпадать с inputType
       * далее 2 варика что может вернуть тело кейса:
       * 1. Нам прокинули сверху
       * 2. Мы уже вывели тип из предыдущих кейсов
       */
      @Override
      public Type visit(org.syntax.stella.Absyn.Match p, Context ctx) {
        Type inputType = p.expr_.accept(this, ctx);


        Type inferredReturnType = null;

        for (org.syntax.stella.Absyn.MatchCase matchCase : p.listmatchcase_) {
          org.syntax.stella.Absyn.AMatchCase c =
                  (org.syntax.stella.Absyn.AMatchCase) matchCase;

          ctx.enterScope();
          Type prevExpectedType = ctx.expectedType;
          ctx.expectedType = inputType;

          c.pattern_.accept(new PatternVisitor(), ctx);

          ctx.expectedType = prevExpectedType;

          /**
           * need for this case
           * match n {
           *   0 => inl(true) as (Bool + Nat) -> infered as TypeSum
           *   succ(x) => inr(0)
           * }
           */
          if (ctx.expectedType == null) {
            ctx.expectedType = inferredReturnType;
          }

          Type bodyType = c.expr_.accept(this, ctx);

          if (ctx.expectedType != null) {
            checkForMismatch(ctx.expectedType, bodyType);
          } else {
            if (inferredReturnType == null) {
              inferredReturnType = bodyType;
            } else {
              checkForMismatch(
                      inferredReturnType,
                      bodyType
              );
            }
          }

          ctx.exitScope();
        }


        return ctx.expectedType!= null ? ctx.expectedType : inferredReturnType;
      }

      public Type visit(
              org.syntax.stella.Absyn.List p,
              Context ctx) { /* Code for List goes here */
        for (org.syntax.stella.Absyn.Expr x : p.listexpr_) {
          x.accept(new ExprVisitor(), ctx);
        }
        return null;
      }

      public Type visit(
              org.syntax.stella.Absyn.Add p,
              Context ctx) { /* Code for Add goes here */
        var t1 = p.expr_1.accept(new ExprVisitor(), ctx);
        var t2 = p.expr_2.accept(new ExprVisitor(), ctx);
        checkThatTypeIsNat(t1);
        checkThatTypeIsNat(t2);

        return new TypeNat();
      }

      public Type visit(
              org.syntax.stella.Absyn.Subtract p,
              Context ctx) { /* Code for Subtract goes here */
        var t1 = p.expr_1.accept(new ExprVisitor(), ctx);
        var t2 = p.expr_2.accept(new ExprVisitor(), ctx);
        checkThatTypeIsNat(t1);
        checkThatTypeIsNat(t2);
        return new TypeNat();
      }

      public Type visit(
              org.syntax.stella.Absyn.LogicOr p,
              Context ctx) { /* Code for LogicOr goes here */
        var t1 = p.expr_1.accept(new ExprVisitor(), ctx);
        var t2 = p.expr_2.accept(new ExprVisitor(), ctx);
        if (!(t1 instanceof TypeBool)) {

          throw new TypeCheckException(
                  TypeCheckException.ErrorType.ERROR_UNEXPECTED_TYPE_FOR_EXPRESSION,
                  new TypeBool(), t1
          );
        }
        if (!(t2 instanceof TypeBool)) {
          throw new TypeCheckException(
                  TypeCheckException.ErrorType.ERROR_UNEXPECTED_TYPE_FOR_EXPRESSION,
                  new TypeBool(), t2
          );
        }
        return new TypeBool();
      }

      public Type visit(
              org.syntax.stella.Absyn.Multiply p,
              Context ctx) { /* Code for Multiply goes here */
        var t1 = p.expr_1.accept(new ExprVisitor(), ctx);
        var t2 = p.expr_2.accept(new ExprVisitor(), ctx);
        checkThatTypeIsNat(t1);
        checkThatTypeIsNat(t2);
        return new TypeNat();
      }

      public Type visit(
              org.syntax.stella.Absyn.Divide p,
              Context ctx) { /* Code for Divide goes here */
        var t1 = p.expr_1.accept(new ExprVisitor(), ctx);
        var t2 = p.expr_2.accept(new ExprVisitor(), ctx);
        checkThatTypeIsNat(t1);
        checkThatTypeIsNat(t2);
        return new TypeNat();
      }

      public Type visit(
              org.syntax.stella.Absyn.LogicAnd p,
              Context ctx) { /* Code for LogicAnd goes here */
        var t1 = p.expr_1.accept(new ExprVisitor(), ctx);
        var t2 = p.expr_2.accept(new ExprVisitor(), ctx);
        if (!(t1 instanceof TypeBool)) {

          throw new TypeCheckException(
                  TypeCheckException.ErrorType.ERROR_UNEXPECTED_TYPE_FOR_EXPRESSION,
                  new TypeBool(), t1
          );
        }
        if (!(t2 instanceof TypeBool)) {
          throw new TypeCheckException(
                  TypeCheckException.ErrorType.ERROR_UNEXPECTED_TYPE_FOR_EXPRESSION,
                  new TypeBool(), t2
          );
        }
        return new TypeBool();
      }

      public Type visit(
              org.syntax.stella.Absyn.Ref p,
              Context ctx) { /* Code for Ref goes here */
        p.expr_.accept(new ExprVisitor(), ctx);
        return null;
      }

      public Type visit(
              org.syntax.stella.Absyn.Deref p,
              Context ctx) { /* Code for Deref goes here */
        p.expr_.accept(new ExprVisitor(), ctx);
        return null;
      }

      public Type visit(org.syntax.stella.Absyn.Application p, Context ctx) {
        var t1 = p.expr_.accept(new ExprVisitor(), ctx);
        checkThatTypeIsTypeFun(t1);

        TypeFun funType = (TypeFun) t1;
        Type expectedParamType = funType.listtype_.get(0);

        Expr argExpr = p.listexpr_.getFirst();

        if (argExpr instanceof org.syntax.stella.Absyn.Abstraction abs) {
          checkForUnexpectedParamType(abs, expectedParamType);
        }
        Type oldExpected = ctx.expectedType; // 1. Сохраняем старый контекст
        ctx.expectedType =
                expectedParamType; // 2. Устанавливаем ожидание для аргумента
        Type t2 = argExpr.accept(new ExprVisitor(), ctx);
        ctx.expectedType = oldExpected;
        checkForMismatch(expectedParamType, t2);

        return funType.type_;
      }

      private void checkForUnexpectedParamType(
              Abstraction abs, Type expectedParamType) {
        AParamDecl paramDecl = (AParamDecl) abs.listparamdecl_.getFirst();
        checkForMismatch(expectedParamType, paramDecl.type_);
      }

      public Type visit(
              org.syntax.stella.Absyn.TypeApplication p,
              Context ctx) { /* Code for TypeApplication goes here */
        p.expr_.accept(new ExprVisitor(), ctx);
        for (org.syntax.stella.Absyn.Type x : p.listtype_) {
          x.accept(new TypeVisitor(), ctx);
        }
        return null;
      }

      public Type visit(org.syntax.stella.Absyn.DotRecord p, Context ctx) {
        Type leftType = p.expr_.accept(new ExprVisitor(), ctx);

        TypeRecord recordType = checkThatTypeIsTypeRecord(leftType);

        String fieldName = p.stellaident_;

        return tryFindField(recordType, fieldName).orElseThrow(
                () -> new TypeCheckException(
                        TypeCheckException.ErrorType.ERROR_UNEXPECTED_FIELD_ACCESS,
                        "Record does not contain field '" + fieldName + "'"
                ));

      }

      private Optional<Type> tryFindField(
              TypeRecord recordType, String fieldName) {
        for (RecordFieldType field : recordType.listrecordfieldtype_) {
          if (field instanceof ARecordFieldType f) {
            if (f.stellaident_.equals(fieldName)) {
              return Optional.of(f.type_);
            }
          }
        }
        return Optional.empty();
      }

      private TypeRecord checkThatTypeIsTypeRecord(Type leftType) {
        if (!(leftType instanceof TypeRecord recordType)) {
          throw new TypeCheckException(
                  TypeCheckException.ErrorType.ERROR_NOT_A_RECORD,
                  "Expected a record type but got: " + leftType
          );
        }
        return recordType;
      }

      public Type visit(
              org.syntax.stella.Absyn.DotTuple p,
              Context ctx) { /* Code for DotTuple goes here */
        Type typeLeft = p.expr_.accept(new ExprVisitor(), ctx);
        TypeTuple tuple = checkThatTypeIsTypeTuple(typeLeft);
        int index = p.integer_;
        int size = tuple.listtype_.size();
        checkTupleBoundaries(index, size);

        return tuple.listtype_.get(index - 1);
      }

      private TypeTuple checkThatTypeIsTypeTuple(Type typeLeft) {
        if (!(typeLeft instanceof TypeTuple tuple)) {
          throw new TypeCheckException(
                  TypeCheckException.ErrorType.ERROR_NOT_A_TUPLE,
                  "Expected a tuple type but got: " + typeLeft
          );
        }
        return tuple;
      }

      private void checkTupleBoundaries(int index, int size) {
        if (index < 1 || index > size) {
          throw new TypeCheckException(
                  TypeCheckException.ErrorType.ERROR_TUPLE_INDEX_OUT_OF_BOUNDS,
                  "Tuple index " + index + " is out of bounds. Tuple size: " + size
          );
        }
      }

      public Type visit(org.syntax.stella.Absyn.Tuple p, Context ctx) {
        ListType componentTypes = new ListType();

        for (org.syntax.stella.Absyn.Expr expr : p.listexpr_) {
          Type type = expr.accept(new ExprVisitor(), ctx);
          componentTypes.add(type);
        }

        return new TypeTuple(componentTypes);
      }

      public Type visit(org.syntax.stella.Absyn.Record p, Context ctx) {
        org.syntax.stella.Absyn.ListRecordFieldType fieldTypes =
                new org.syntax.stella.Absyn.ListRecordFieldType();
        java.util.Set<String> seenFields = new java.util.HashSet<>();

        for (org.syntax.stella.Absyn.Binding binding : p.listbinding_) {
          org.syntax.stella.Absyn.ABinding b =
                  (org.syntax.stella.Absyn.ABinding) binding;

          if (seenFields.contains(b.stellaident_)) {
            throw new TypeCheckException(
                    TypeCheckException.ErrorType.ERROR_DUPLICATE_RECORD_FIELDS,
                    "Duplicate field '" + b.stellaident_ + "' in record construction"
            );
          }
          seenFields.add(b.stellaident_);

          Type fieldType = b.expr_.accept(new ExprVisitor(), ctx);

          fieldTypes.add(
                  new org.syntax.stella.Absyn.ARecordFieldType(
                          b.stellaident_, fieldType));
        }

        return new org.syntax.stella.Absyn.TypeRecord(fieldTypes);
      }

      public Type visit(
              org.syntax.stella.Absyn.ConsList p,
              Context ctx) { /* Code for ConsList goes here */
        p.expr_1.accept(new ExprVisitor(), ctx);
        p.expr_2.accept(new ExprVisitor(), ctx);
        return null;
      }

      public Type visit(
              org.syntax.stella.Absyn.Head p,
              Context ctx) { /* Code for Head goes here */
        p.expr_.accept(new ExprVisitor(), ctx);
        return null;
      }

      public Type visit(
              org.syntax.stella.Absyn.IsEmpty p,
              Context ctx) { /* Code for IsEmpty goes here */
        p.expr_.accept(new ExprVisitor(), ctx);
        return null;
      }

      public Type visit(
              org.syntax.stella.Absyn.Tail p,
              Context ctx) { /* Code for Tail goes here */
        p.expr_.accept(new ExprVisitor(), ctx);
        return null;
      }

      public Type visit(
              org.syntax.stella.Absyn.Panic p,
              Context ctx) { /* Code for Panic goes here */
        return null;
      }

      public Type visit(
              org.syntax.stella.Absyn.Throw p,
              Context ctx) { /* Code for Throw goes here */
        p.expr_.accept(new ExprVisitor(), ctx);
        return null;
      }

      public Type visit(
              org.syntax.stella.Absyn.TryCatch p,
              Context ctx) { /* Code for TryCatch goes here */
        p.expr_1.accept(new ExprVisitor(), ctx);
        p.pattern_.accept(new PatternVisitor(), ctx);
        p.expr_2.accept(new ExprVisitor(), ctx);
        return null;
      }

      public Type visit(
              org.syntax.stella.Absyn.TryWith p,
              Context ctx) { /* Code for TryWith goes here */
        p.expr_1.accept(new ExprVisitor(), ctx);
        p.expr_2.accept(new ExprVisitor(), ctx);
        return null;
      }

      public Type visit(
              org.syntax.stella.Absyn.TryCastAs p,
              Context ctx) { /* Code for TryCastAs goes here */
        p.expr_1.accept(new ExprVisitor(), ctx);
        p.type_.accept(new TypeVisitor(), ctx);
        p.pattern_.accept(new PatternVisitor(), ctx);
        p.expr_2.accept(new ExprVisitor(), ctx);
        p.expr_3.accept(new ExprVisitor(), ctx);
        return null;
      }

      public Type visit(
              org.syntax.stella.Absyn.Inl p,
              Context ctx) { /* Code for Inl goes here */
        if (!(ctx.expectedType instanceof TypeSum ts)) {
          throw new TypeCheckException(
                  TypeCheckException.ErrorType.ERROR_UNEXPECTED_INJECTION,
                  "Expected a sum type for Inl but got: " + ctx.expectedType
          );
        }
        Type prevExpected = ctx.expectedType;
        ctx.expectedType = ts.type_1;
        var t1 = p.expr_.accept(new ExprVisitor(), ctx);
        ctx.expectedType = prevExpected;
        checkForMismatch(t1, ts.type_1);
        return ctx.expectedType;
      }

      public Type visit(
              org.syntax.stella.Absyn.Inr p,
              Context ctx) { /* Code for Inr goes here */
        if (!(ctx.expectedType instanceof TypeSum ts)) {
          throw new TypeCheckException(
                  TypeCheckException.ErrorType.ERROR_UNEXPECTED_INJECTION,
                  "Expected a sum type for Inr but got: " + ctx.expectedType
          );
        }
        Type prevExpected = ctx.expectedType;
        ctx.expectedType = ts.type_2;
        var t1 = p.expr_.accept(new ExprVisitor(), ctx);
        ctx.expectedType = prevExpected;
        checkForMismatch(t1, ts.type_2);
        return ctx.expectedType;
      }

      public Type visit(
              org.syntax.stella.Absyn.Succ p,
              Context ctx) { /* Code for Succ goes here */
        var t1 = p.expr_.accept(new ExprVisitor(), ctx);
        checkThatTypeIsNat(t1);
        return new TypeNat();
      }

      public Type visit(
              org.syntax.stella.Absyn.LogicNot p,
              Context ctx) { /* Code for LogicNot goes here */
        var t1 = p.expr_.accept(new ExprVisitor(), ctx);
        if (!(t1 instanceof TypeBool)) {
          throw new TypeCheckException(
                  TypeCheckException.ErrorType.ERROR_UNEXPECTED_TYPE_FOR_EXPRESSION,
                  new TypeBool(), t1
          );
        }
        return new TypeBool();
      }

      public Type visit(
              org.syntax.stella.Absyn.Pred p,
              Context ctx) { /* Code for Pred goes here */
        var t1 = p.expr_.accept(new ExprVisitor(), ctx);
        checkThatTypeIsNat(t1);
        return new TypeNat();
      }

      public Type visit(
              org.syntax.stella.Absyn.IsZero p,
              Context ctx) { /* Code for IsZero goes here */
        var t1 = p.expr_.accept(new ExprVisitor(), ctx);
        checkThatTypeIsNat(t1);
        return new TypeBool();
      }

      // Γ ` t1 : T1→T1
      //---------------------
      //Γ ` fix t1 : T1
      public Type visit(org.syntax.stella.Absyn.Fix p, Context ctx) {
        Type inferredType = p.expr_.accept(new ExprVisitor(), ctx);


        TypeFun funType = checkThatTypeIsTypeFun(inferredType);

        Type argType = funType.listtype_.getFirst();
        Type retType = funType.type_;

        checkForMismatch(argType, retType);


        return argType;
      }

      private TypeFun checkThatTypeIsTypeFun(Type inferredType) {
        if (!(inferredType instanceof TypeFun funType)) {
          throw new TypeCheckException(
                  TypeCheckException.ErrorType.ERROR_NOT_A_FUNCTION,
                  "Expected a function type for fixpoint combinator but got: " + inferredType
          );
        }
        return funType;
      }

      public Type visit(
              org.syntax.stella.Absyn.NatRec p,
              Context ctx) { /* Code for NatRec goes here */

        var t1 = p.expr_1.accept(new ExprVisitor(), ctx); // n
        var t2 = p.expr_2.accept(new ExprVisitor(), ctx); // z (Initial value)
        var t3 = p.expr_3.accept(new ExprVisitor(), ctx); // s (Step function)

        checkThatReturnTypeIsAFunctionThatReturnsFunction(t3);
        checkThatTypeIsNat(t1);
        Type T = t2;

        // expected fn(Nat) -> (fn(T) -> T)

        // fn(T) -> T
        ListType innerParamTypes = new ListType();
        innerParamTypes.add(T);
        TypeFun innerFunType = new TypeFun(innerParamTypes, T);

        // fn(Nat) -> InnerFun
        ListType outerParamTypes = new ListType();
        outerParamTypes.add(new TypeNat());
        Type expectedStepType = new TypeFun(outerParamTypes, innerFunType);

        checkForMismatch(expectedStepType, t3);

        return T;
      }


      private void checkThatTypeIsNat(Type t1) {
        if (!(t1 instanceof TypeNat)) {
          throw new TypeCheckException(
                  TypeCheckException.ErrorType.ERROR_UNEXPECTED_TYPE_FOR_EXPRESSION,
                  new TypeNat(), t1
          );
        }
      }

      private void checkThatReturnTypeIsAFunctionThatReturnsFunction(Type t3) {
        if (!(t3 instanceof TypeFun tf && tf.type_ instanceof TypeFun)) {
          throw new TypeCheckException(
                  TypeCheckException.ErrorType.ERROR_NOT_A_FUNCTION,
                  "Expected a function type for step function (3rd arg) but got: " + t3

          );
        }
      }

      public Type visit(
              org.syntax.stella.Absyn.Fold p,
              Context ctx) { /* Code for Fold goes here */
        p.type_.accept(new TypeVisitor(), ctx);
        p.expr_.accept(new ExprVisitor(), ctx);
        return null;
      }

      public Type visit(
              org.syntax.stella.Absyn.Unfold p,
              Context ctx) { /* Code for Unfold goes here */
        p.type_.accept(new TypeVisitor(), ctx);
        p.expr_.accept(new ExprVisitor(), ctx);
        return null;
      }

      public Type visit(
              org.syntax.stella.Absyn.ConstTrue p,
              Context ctx) { /* Code for ConstTrue goes here */
        return new TypeBool();
      }

      public Type visit(
              org.syntax.stella.Absyn.ConstFalse p,
              Context ctx) { /* Code for ConstFalse goes here */
        return new TypeBool();
      }

      public Type visit(
              org.syntax.stella.Absyn.ConstUnit p,
              Context ctx) { /* Code for ConstUnit goes here */
        return new TypeUnit();
      }

      public Type visit(
              org.syntax.stella.Absyn.ConstInt p,
              Context ctx) { /* Code for ConstInt goes here */
        if (p.integer_ < 0) {
          throw new TypeCheckException(
                  TypeCheckException.ErrorType.ERROR_ILLEGAL_NEGATIVE_LITERAL,
                  "Negative literal " + p.integer_ + " is not allowed"
          );
        }
        return new TypeNat();
      }

      public Type visit(
              org.syntax.stella.Absyn.ConstMemory p,
              Context ctx) { /* Code for ConstMemory goes here */

        return null;
      }

      public Type visit(
              org.syntax.stella.Absyn.Var p,
              Context ctx) { /* Code for Var goes here */
        return ctx.lookup(p.stellaident_)
                .orElseThrow(() -> new TypeCheckException(
                        TypeCheckException.ErrorType.ERROR_UNDEFINED_VARIABLE,
                        "Variable " + p.stellaident_ + "is not defined."
                ));
      }
    }

    public class PatternBindingVisitor implements org.syntax.stella.Absyn.PatternBinding.Visitor<Type, Context> {
      public Type visit(
              org.syntax.stella.Absyn.APatternBinding p,
              Context ctx) { /* Code for APatternBinding goes here */
        p.pattern_.accept(new PatternVisitor(), ctx);
        p.expr_.accept(new ExprVisitor(), ctx);
        return null;
      }
    }

    public class VariantFieldTypeVisitor implements org.syntax.stella.Absyn.VariantFieldType.Visitor<Type, Context> {
      public Type visit(
              org.syntax.stella.Absyn.AVariantFieldType p,
              Context ctx) { /* Code for AVariantFieldType goes here */

        p.optionaltyping_.accept(new OptionalTypingVisitor(), ctx);
        return null;
      }
    }

    public class RecordFieldTypeVisitor implements org.syntax.stella.Absyn.RecordFieldType.Visitor<Type, Context> {
      public Type visit(
              org.syntax.stella.Absyn.ARecordFieldType p,
              Context ctx) { /* Code for ARecordFieldType goes here */

        return p.type_.accept(new TypeVisitor(), ctx);
      }
    }

    public class TypingVisitor implements org.syntax.stella.Absyn.Typing.Visitor<Type, Context> {
      public Type visit(
              org.syntax.stella.Absyn.ATyping p,
              Context ctx) { /* Code for ATyping goes here */
        p.expr_.accept(new ExprVisitor(), ctx);
        p.type_.accept(new TypeVisitor(), ctx);
        return null;
      }
    }
  }
}