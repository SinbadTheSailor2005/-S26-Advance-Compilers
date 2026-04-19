

package org.stella.typecheck;

import org.stella.typecheck.exceptions.TypeCheckException;
import org.syntax.stella.Absyn.*;
import org.syntax.stella.Absyn.List;
import org.syntax.stella.Absyn.Record;

import java.util.*;

import static org.stella.typecheck.VisitTypeCheck.ProgramVisitor.getOptionalTypingType;
import static org.stella.typecheck.VisitTypeCheck.ProgramVisitor.isSubtype;

/*** Visitor Design Pattern Skeleton. ***/

/* This implements the common visitor design pattern.
   Tests show it to be slightly less efficient than the
   instanceof method, but easier to use.
   Replace the R and A parameters with the desired return
   and context types.*/

public class VisitTypeCheck {
  private int typeVarCounter = 1;

  // хранит найденные соответствия типов , eg 1T -> Nat
  private final Map<String, Type> substitutions = new HashMap<>();

  public TypeVar freshTypeVar() {
    return new TypeVar("?T" + (typeVarCounter++));
  }

  public Type resolve(Type t) {
    if (t instanceof TypeVar tv) {
      String varName = tv.stellaident_;
      if (substitutions.containsKey(varName)) {
        return resolve(substitutions.get(varName));
      }
    }
    return t; // Если это не переменная или её нет в мапе, возвращаем как есть
  }

  public void unify(Type expected, Type actual, Context ctx) {
    Type t1 = resolve(expected);
    Type t2 = resolve(actual);

    if (isSameType(t1, t2)) return;

    if (ctx.isSubtypingEnabled() && isSubtype(t2, t1, ctx)) {
      return;
    }

    if (t1 instanceof TypeVar v1) {
      bindVar(v1.stellaident_, t2);
      return;
    }

    if (t2 instanceof TypeVar v2) {
      bindVar(v2.stellaident_, t1);
      return;
    }

    if (t1 instanceof TypeFun f1 && t2 instanceof TypeFun f2) {
      if (f1.listtype_.size() != f2.listtype_.size()) throwMismatch(t1, t2);
      for (int i = 0; i < f1.listtype_.size(); i++) {
        unify(f1.listtype_.get(i), f2.listtype_.get(i), ctx);
      }
      unify(f1.type_, f2.type_, ctx);
      return;
    }

    if (t1 instanceof TypeList l1 && t2 instanceof TypeList l2) {
      unify(l1.type_, l2.type_, ctx);
      return;
    }

    if (t1 instanceof TypeTuple tup1 && t2 instanceof TypeTuple tup2) {
      if (tup1.listtype_.size() != tup2.listtype_.size())
        throwMismatch(t1, tup2);
      for (int i = 0; i < tup1.listtype_.size(); i++) {
        unify(tup1.listtype_.get(i), tup2.listtype_.get(i), ctx);
      }
      return;
    }

    if (t1 instanceof TypeSum s1 && t2 instanceof TypeSum s2) {
      unify(s1.type_1, s2.type_1, ctx);
      unify(s1.type_2, s2.type_2, ctx);
      return;
    }

    if (t1 instanceof TypeRef r1 && t2 instanceof TypeRef r2) {
      unify(r1.type_, r2.type_, ctx);
      return;
    }
    throwMismatch(t1, t2);
  }

  private void bindVar(String varName, Type type) {
    if (occursIn(varName, type)) {
      throw new TypeCheckException(
              TypeCheckException.ErrorType.ERROR_OCCURS_CHECK_INFINITE_TYPE,
              "Infinite type: " + varName + " in " + TypePretty.pretty(type)
      );
    }
    substitutions.put(varName, type);
  }


  private boolean occursIn(String varName, Type t) {
    Type resolvedType = resolve(t);

    if (resolvedType instanceof TypeVar tv) {
      return tv.stellaident_.equals(varName);
    }

    if (resolvedType instanceof TypeFun tf) {
      for (Type paramType : tf.listtype_) {
        if (occursIn(varName, paramType)) return true;
      }
      return occursIn(varName, tf.type_);
    }

    if (resolvedType instanceof TypeTuple tt) {
      for (Type elemType : tt.listtype_) {
        if (occursIn(varName, elemType)) return true;
      }
      return false;
    }

    if (resolvedType instanceof TypeList tl) {
      return occursIn(varName, tl.type_);
    }

    if (resolvedType instanceof TypeSum ts) {
      return occursIn(varName, ts.type_1) || occursIn(varName, ts.type_2);
    }

    if (resolvedType instanceof TypeRef tr) {
      return occursIn(varName, tr.type_);
    }

    if (resolvedType instanceof TypeRecord trc) {
      for (RecordFieldType f : trc.listrecordfieldtype_) {
        ARecordFieldType field = (ARecordFieldType) f;
        if (occursIn(varName, field.type_)) return true;
      }
      return false;
    }

    if (resolvedType instanceof TypeVariant tv) {
      for (VariantFieldType f : tv.listvariantfieldtype_) {
        AVariantFieldType vField = (AVariantFieldType) f;
        Type innerType = getOptionalTypingType(vField.optionaltyping_);
        if (innerType != null && occursIn(varName, innerType)) {
          return true;
        }
      }
      return false;
    }

    return false;
  }

  private void throwMismatch(Type expected, Type actual) {
    throw new TypeCheckException(
            TypeCheckException.ErrorType.ERROR_UNEXPECTED_TYPE_FOR_EXPRESSION,
            expected, actual
    );
  }

  public static Type substitute(Type t, String varName, Type replacement) {
    if (t == null) return null;

    if (t instanceof TypeVar tv) {
      if (tv.stellaident_.equals(varName)) {
        return replacement;
      }
      return tv;
    }
    if (t instanceof TypeFun tf) {
      ListType newParams = new ListType();
      for (Type p : tf.listtype_)
        newParams.add(substitute(p, varName, replacement));
      return new TypeFun(newParams, substitute(tf.type_, varName, replacement));
    }
    if (t instanceof TypeForAll tfa) {
      if (tfa.liststellaident_.contains(varName)) {
        return tfa;
      }
      return new TypeForAll(
              tfa.liststellaident_,
              substitute(tfa.type_, varName, replacement)
      );
    }
    if (t instanceof TypeList tl) {
      return new TypeList(substitute(tl.type_, varName, replacement));
    }
    if (t instanceof TypeTuple tt) {
      ListType newTypes = new ListType();
      for (Type type : tt.listtype_)
        newTypes.add(substitute(type, varName, replacement));
      return new TypeTuple(newTypes);
    }
    if (t instanceof TypeSum ts) {
      return new TypeSum(
              substitute(ts.type_1, varName, replacement),
              substitute(ts.type_2, varName, replacement)
      );
    }
    if (t instanceof TypeRef tr) {
      return new TypeRef(substitute(tr.type_, varName, replacement));
    }
    return t;
  }

  public static Optional<AVariantFieldType> getAVariantFieldType(
          String stellaident_, TypeVariant typeVariant) {
    for (VariantFieldType f : typeVariant.listvariantfieldtype_) {
      AVariantFieldType af = (AVariantFieldType) f;
      if (af.stellaident_.equals(stellaident_)) {
        return Optional.of(af);

      }
    }
    return Optional.empty();
  }

  public static boolean isSameType(Type t1, Type t2) {
    if (t1 == null || t2 == null) return false;
    if (t1 instanceof TypeTop && t2 instanceof TypeTop) return true;
    if (t1 instanceof TypeBottom && t2 instanceof TypeBottom) return true;
    if (t1 instanceof TypeNat && t2 instanceof TypeNat) return true;
    if (t1 instanceof TypeBool && t2 instanceof TypeBool) return true;
    if (t1 instanceof TypeUnit && t2 instanceof TypeUnit) return true;
    if (t1 instanceof TypeForAll tfa1 && t2 instanceof TypeForAll tfa2) {
      if (tfa1.liststellaident_.size() != tfa2.liststellaident_.size())
        return false;

      Type body2 = tfa2.type_;
      // Подставляем имена переменных из первого типа во второй
      for (int i = 0; i < tfa1.liststellaident_.size(); i++) {
        String var1 = tfa1.liststellaident_.get(i);
        String var2 = tfa2.liststellaident_.get(i);
        body2 = substitute(body2, var2, new TypeVar(var1));
      }
      return isSameType(tfa1.type_, body2);
    }

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

    if (t1 instanceof TypeRef tr1 && t2 instanceof TypeRef tr2) {
      return isSameType(tr1.type_, tr2.type_);
    }

    if (t1 instanceof TypeVar tv1 && t2 instanceof TypeVar tv2) {
      return tv1.stellaident_.equals(tv2.stellaident_);
    }
    if (t1 instanceof TypeSum ts1 && t2 instanceof TypeSum ts2) {
      return isSameType(ts1.type_1, ts2.type_1) && isSameType(
              ts1.type_2, ts2.type_2);
    }


    if (t1 instanceof TypeRecord tr1 && t2 instanceof TypeRecord tr2) {
      if (tr1.listrecordfieldtype_.size() != tr2.listrecordfieldtype_.size())
        return false;

      for (RecordFieldType f1_abstract : tr1.listrecordfieldtype_) {
        ARecordFieldType f1 =
                (ARecordFieldType) f1_abstract;

        boolean found = false;

        for (RecordFieldType f2_abstract : tr2.listrecordfieldtype_) {
          ARecordFieldType f2 =
                  (ARecordFieldType) f2_abstract;

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
    if (t1 instanceof TypeVariant tv1 && t2 instanceof TypeVariant tv2) {
      if (tv1.listvariantfieldtype_.size() != tv2.listvariantfieldtype_.size())
        return false;

      for (VariantFieldType f1_abstract : tv1.listvariantfieldtype_) {
        AVariantFieldType f1 =
                (AVariantFieldType) f1_abstract;

        boolean found = false;

        for (VariantFieldType f2_abstract : tv2.listvariantfieldtype_) {
          AVariantFieldType f2 =
                  (AVariantFieldType) f2_abstract;

          if (f1.stellaident_.equals(f2.stellaident_)) {
            Type inner1 = getOptionalTypingType(f1.optionaltyping_);
            Type inner2 = getOptionalTypingType(f2.optionaltyping_);

            if (inner1 != null && inner2 != null) {
              if (!isSameType(inner1, inner2)) {
                return false;
              }
            } else if (inner1 != null || inner2 != null) {
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

  public class ProgramVisitor implements Program.Visitor<Type, Context> {
    public Type visit(
            AProgram p,
            Context ctx) { /* Code for AProgram goes here */
      p.languagedecl_.accept(new LanguageDeclVisitor(), ctx);
//      for (org.syntax.stella.Absyn.Extension x : p.listextension_) {
//        x.accept(new ExtensionVisitor(), ctx);
//      }
      checkMain(p.listdecl_);
      ctx.enterScope();
      addGlobalFunctionsToGlobalScope(p, ctx);
      addExtensionsToGlobalScope(p, ctx);

      for (Decl x : p.listdecl_) {
        x.accept(new DeclVisitor(), ctx);
      }
      return null;
    }

    private void addExtensionsToGlobalScope(AProgram p, Context ctx) {
      for (var listExt : p.listextension_) {
        if (listExt instanceof AnExtension anExtension)
          for (var ext : anExtension.listextensionname_) {
            ctx.addExtension(ext);
          }
      }
    }

    private void addGlobalFunctionsToGlobalScope(AProgram p, Context ctx) {
      for (Decl x : p.listdecl_) {
        if (x instanceof DeclFun df) {
          Type argType = ((AParamDecl) df.listparamdecl_.getFirst()).type_;
          Type returnType =
                  (df.returntype_ instanceof SomeReturnType srt) ? srt.type_ : new TypeUnit();
          ListType lt = new ListType();
          lt.add(argType);
          ctx.addVariable(df.stellaident_, new TypeFun(lt, returnType));
        } else if (x instanceof DeclFunGeneric dfg) {
          Type argType = ((AParamDecl) dfg.listparamdecl_.getFirst()).type_;
          Type returnType =
                  (dfg.returntype_ instanceof SomeReturnType srt) ? srt.type_ : new TypeUnit();
          ListType lt = new ListType();
          lt.add(argType);
          TypeFun funType = new TypeFun(lt, returnType);

          org.syntax.stella.Absyn.ListStellaIdent typeVars =
                  new org.syntax.stella.Absyn.ListStellaIdent();
          typeVars.addAll(dfg.liststellaident_);

          // Тип дженерик-функции — это forall X. fn(T) -> R
          ctx.addVariable(dfg.stellaident_, new TypeForAll(typeVars, funType));
        }
      }
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

    public static class LanguageDeclVisitor implements LanguageDecl.Visitor<Type, Context> {
      public Type visit(
              LanguageCore p,
              Context ctx) { /* Code for LanguageCore goes here */
        return null;
      }
    }

    public static class ExtensionVisitor implements Extension.Visitor<Type, Context> {
      public Type visit(
              AnExtension p,
              Context ctx) { /* Code for AnExtension goes here */
        // Extensions are ignored in this typechecker implementation.
        return null;
      }
    }

    public static boolean isSubtype(Type S, Type T, Context ctx) {
      if (!ctx.isSubtypingEnabled()) {
        return isSameType(S, T);
      }

      if (isSameType(S, T)) return true;

      if (S instanceof TypeBottom) return true;
      if (T instanceof TypeTop) return true;

      if (S instanceof TypeFun funS && T instanceof TypeFun funT) {
        if (funS.listtype_.size() != funT.listtype_.size()) return false;
        // T_arg <: S_arg
        for (int i = 0; i < funS.listtype_.size(); i++) {
          if (!isSubtype(funT.listtype_.get(i), funS.listtype_.get(i), ctx))
            return false;
        }
        // S_ret <: T_ret
        return isSubtype(funS.type_, funT.type_, ctx);
      }

      if (S instanceof TypeRecord recS && T instanceof TypeRecord recT) {
        // У подтипа обязаны быть все поля супертипа
        for (RecordFieldType fT : recT.listrecordfieldtype_) {
          ARecordFieldType afT = (ARecordFieldType) fT;
          ARecordFieldType afS = findRecordField(recS, afT.stellaident_);

          if (afS == null) return false;
          if (!isSubtype(afS.type_, afT.type_, ctx))
            return false; // Типы полей ковариантны
        }
        return true;
      }

      if (S instanceof TypeVariant varS && T instanceof TypeVariant varT) {
        // тут наоборот у супертипа обязаны быть все варианты подтипа
        for (VariantFieldType fS : varS.listvariantfieldtype_) {
          AVariantFieldType afS = (AVariantFieldType) fS;
          AVariantFieldType afT = findVariantField(varT, afS.stellaident_);

          if (afT == null) return false;

          Type innerS = getOptionalTypingType(afS.optionaltyping_);
          Type innerT = getOptionalTypingType(afT.optionaltyping_);

          if (innerS != null && innerT != null) {
            if (!isSubtype(innerS, innerT, ctx)) return false;
          } else if (innerS != null || innerT != null) {
            return false;
          }
        }
        return true;
      }
      if (S instanceof TypeTuple tupS && T instanceof TypeTuple tupT) {
        if (tupS.listtype_.size() != tupT.listtype_.size()) return false;
        for (int i = 0; i < tupS.listtype_.size(); i++) {
          if (!isSubtype(tupS.listtype_.get(i), tupT.listtype_.get(i), ctx))
            return false;
        }
        return true;
      }
      if (S instanceof TypeList listS && T instanceof TypeList listT) {
        return isSubtype(listS.type_, listT.type_, ctx);
      }

      if (S instanceof TypeSum sumS && T instanceof TypeSum sumT) {
        return isSubtype(sumS.type_1, sumT.type_1, ctx) && isSubtype(
                sumS.type_2, sumT.type_2, ctx);
      }

      // ref Инвариантны
      if (S instanceof TypeRef refS && T instanceof TypeRef refT) {
        return isSubtype(refS.type_, refT.type_, ctx) && isSubtype(
                refT.type_, refS.type_, ctx);
      }

      return false;
    }


    public static void checkVariantMismatch(
            TypeVariant expected,
            TypeVariant actual,
            Context ctx) {

      Set<String> expectedLabels = new HashSet<>();
      for (VariantFieldType f : expected.listvariantfieldtype_) {
        expectedLabels.add(((AVariantFieldType) f).stellaident_);
      }

      Set<String> actualLabels = new HashSet<>();
      for (VariantFieldType f : actual.listvariantfieldtype_) {
        actualLabels.add(((AVariantFieldType) f).stellaident_);
      }

      // В сабтайпинге подтип НЕ может иметь лишних лейблов, которых нет в супертипе.
      java.util.List<String> unexpected = new ArrayList<>();
      for (String label : actualLabels) {
        if (!expectedLabels.contains(label)) unexpected.add(label);
      }
      if (!unexpected.isEmpty()) {
        throw new TypeCheckException(
                TypeCheckException.ErrorType.ERROR_UNEXPECTED_VARIANT_LABEL,
                // Или другая подходящая ошибка
                "Variant type has unexpected labels: " + String.join(
                        ", ", unexpected)
        );
      }

      // Но подтип МОЖЕТ не иметь некоторых лейблов супертипа (если сабтайпинг включен).
      java.util.List<String> missing = new ArrayList<>();
      for (String label : expectedLabels) {
        if (!actualLabels.contains(label)) missing.add(label);
      }
      if (!missing.isEmpty()) {
        if (!ctx.isSubtypingEnabled()) {
          throw new TypeCheckException(
                  TypeCheckException.ErrorType.ERROR_MISSING_VARIANT_LABELS,
                  "Variant type is missing required labels: " + String.join(
                          ", ", missing)
          );
        }
      }
    }

    public static void checkRecordMismatch(
            TypeRecord expected,
            TypeRecord actual,
            Context ctx) {

      Set<String> expectedFields = new HashSet<>();
      for (RecordFieldType f : expected.listrecordfieldtype_) {
        expectedFields.add(((ARecordFieldType) f).stellaident_);
      }

      Set<String> actualFields = new HashSet<>();
      for (RecordFieldType f : actual.listrecordfieldtype_) {
        actualFields.add(((ARecordFieldType) f).stellaident_);
      }

      // Подтип ОБЯЗАН иметь все поля супертипа. Отсутствие поля - всегда ошибка.
      java.util.List<String> missing = new ArrayList<>();
      for (String f : expectedFields) {
        if (!actualFields.contains(f)) missing.add(f);
      }
      if (!missing.isEmpty()) {
        throw new TypeCheckException(
                TypeCheckException.ErrorType.ERROR_MISSING_RECORD_FIELDS,
                "Record is missing required fields: " + String.join(
                        ", ", missing)
        );
      }

      // Подтип МОЖЕТ иметь дополнительные поля, если сабтайпинг включен.
      java.util.List<String> unexpected = new ArrayList<>();
      for (String f : actualFields) {
        if (!expectedFields.contains(f)) unexpected.add(f);
      }
      if (!unexpected.isEmpty()) {
        if (!ctx.isSubtypingEnabled()) {
          throw new TypeCheckException(
                  TypeCheckException.ErrorType.ERROR_UNEXPECTED_RECORD_FIELDS,
                  "Record contains unexpected fields: " + String.join(
                          ", ", unexpected)
          );
        }
      }
    }

    // Хелперы для чистоты кода
    private static ARecordFieldType findRecordField(
            TypeRecord rec, String name) {
      for (RecordFieldType f : rec.listrecordfieldtype_) {
        ARecordFieldType af = (ARecordFieldType) f;
        if (af.stellaident_.equals(name)) return af;
      }
      return null;
    }

    private static AVariantFieldType findVariantField(
            TypeVariant var, String name) {
      for (VariantFieldType f : var.listvariantfieldtype_) {
        AVariantFieldType af = (AVariantFieldType) f;
        if (af.stellaident_.equals(name)) return af;
      }
      return null;
    }

    public static Type getOptionalTypingType(
            OptionalTyping opt) {
      if (opt instanceof SomeTyping) {
        return ((SomeTyping) opt).type_;
      }
      return null;
    }

    public class DeclVisitor implements Decl.Visitor<Type, Context> {
      public Type visit(DeclFun p, Context ctx) {
        ctx.enterScope();

        addParametersToContext(p, ctx);

        addMethodDeclarationsToContext(p, ctx);

        for (Decl decl : p.listdecl_) {
          decl.accept(this, ctx);
        }

        Type expectedRetType =
                p.returntype_.accept(new ReturnTypeVisitor(), ctx);
        ctx.pushExpectedType(expectedRetType);
        Type actualRetType = p.expr_.accept(new ExprVisitor(), ctx);
        ctx.popExpectedType();
        unify(expectedRetType, actualRetType, ctx);


        ctx.exitScope();

        return actualRetType;
      }

      private void addMethodDeclarationsToContext(DeclFun p, Context ctx) {
        for (Decl decl : p.listdecl_) {
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
      }

      private void addParametersToContext(DeclFun p, Context ctx) {
        for (ParamDecl x : p.listparamdecl_) {
          if (x instanceof AParamDecl ap) {
            ctx.addVariable(ap.stellaident_, ap.type_);
          }
        }
      }


      public Type visit(DeclFunGeneric p, Context ctx) {
        ctx.enterScope();
        ctx.enterTypeVarScope();

        for (String typeVar : p.liststellaident_) {
          ctx.addTypeVariable(typeVar);
        }

        for (ParamDecl x : p.listparamdecl_) {
          if (x instanceof AParamDecl ap) {
            ap.type_.accept(
                    new TypeVisitor(),
                    ctx
            );
            ctx.addVariable(ap.stellaident_, ap.type_);
          }
        }

        Type expectedRetType =
                p.returntype_.accept(new ReturnTypeVisitor(), ctx);
        ctx.pushExpectedType(expectedRetType);

        Type actualRetType = p.expr_.accept(new ExprVisitor(), ctx);

        ctx.popExpectedType();
        unify(expectedRetType, actualRetType, ctx);

        ctx.popTypeVarScope();
        ctx.exitScope();
        return null;
      }

      public Type visit(
              DeclTypeAlias p,
              Context ctx) { /* Code for DeclTypeAlias goes here */

        p.type_.accept(new TypeVisitor(), ctx);
        return null;
      }

      public Type visit(
              DeclExceptionType p,
              Context ctx) { /* Code for DeclExceptionType goes here */
        ctx.setExceptionType(p.type_);
        return p.type_.accept(new TypeVisitor(), ctx);
      }

      public Type visit(
              DeclExceptionVariant p,
              Context ctx) { /* Code for DeclExceptionVariant goes here */

        p.type_.accept(new TypeVisitor(), ctx);
        return null;
      }
    }

    // ------------------
    public class LocalDeclVisitor implements LocalDecl.Visitor<Type, Context> {
      public Type visit(
              ALocalDecl p,
              Context ctx) { /* Code for ALocalDecl goes here */
        p.decl_.accept(new DeclVisitor(), ctx);
        return null;
      }
    }

    // ------------------
    public static class AnnotationVisitor implements Annotation.Visitor<Type, Context> {
      public Type visit(
              InlineAnnotation p,
              Context ctx) { /* Code for InlineAnnotation goes here */
        return null;
      }
    }

    // ------------------
    public class ParamDeclVisitor implements ParamDecl.Visitor<Type, Context> {
      public Type visit(
              AParamDecl p,
              Context ctx) { /* Code for AParamDecl goes here */

        return p.type_.accept(new TypeVisitor(), ctx);
      }
    }

    // ------------------
    public class ReturnTypeVisitor implements ReturnType.Visitor<Type, Context> {
      public Type visit(
              NoReturnType p,
              Context ctx) { /* Code for NoReturnType goes here */
        return new TypeUnit();
      }

      public Type visit(
              SomeReturnType p,
              Context ctx) { /* Code for SomeReturnType goes here */
        return p.type_.accept(new TypeVisitor(), ctx);
      }
    }

    // ------------------
    public class ThrowTypeVisitor implements ThrowType.Visitor<Type, Context> {
      public Type visit(
              NoThrowType p,
              Context ctx) { /* Code for NoThrowType goes here */
        return null;
      }

      public Type visit(
              SomeThrowType p,
              Context ctx) { /* Code for SomeThrowType goes here */
        for (Type x : p.listtype_) {
          x.accept(new TypeVisitor(), ctx);
        }
        return null;
      }
    }

    // ------------------

    /**
     * просто чекаем дуюдикаты
     */
    public class TypeVisitor implements Type.Visitor<Type, Context> {
      @Override
      public Type visit(TypeRecord p, Context ctx) {
        Set<String> fields = new HashSet<>();
        for (RecordFieldType f : p.listrecordfieldtype_) {
          ARecordFieldType field =
                  (ARecordFieldType) f;

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

      @Override
      public Type visit(TypeVariant p, Context ctx) {
        Set<String> labels = new HashSet<>();
        for (VariantFieldType f : p.listvariantfieldtype_) {
          AVariantFieldType field =
                  (AVariantFieldType) f;

          if (labels.contains(field.stellaident_)) {
            throw new TypeCheckException(
                    TypeCheckException.ErrorType.ERROR_DUPLICATE_VARIANT_TYPE_FIELDS,
                    "Duplicate label '" + field.stellaident_ + "'"
            );
          }
          labels.add(field.stellaident_);

          field.accept(new VariantFieldTypeVisitor(), ctx);
        }
        return p;
      }

      // TODO: Diferrence TypeRec vs TypeRecord
      public Type visit(TypeRec p, Context ctx) {
        return p;
      }

      public Type visit(TypeFun p, Context ctx) {
        // assumed that 1 param only
        for (Type paramType : p.listtype_)
          paramType.accept(this, ctx);
        p.type_.accept(this, ctx);
        return p;
      }

      public Type visit(TypeTuple p, Context ctx) {
        for (Type t : p.listtype_) t.accept(this, ctx);
        return p;
      }

      public Type visit(TypeSum p, Context ctx) {
        p.type_1.accept(this, ctx);
        p.type_2.accept(this, ctx);
        return p;
      }

      public Type visit(TypeList p, Context ctx) {
        p.type_.accept(this, ctx);
        return p;
      }

      public Type visit(TypeNat p, Context ctx) {
        return p;
      }

      public Type visit(TypeBool p, Context ctx) {
        return p;
      }

      public Type visit(TypeUnit p, Context ctx) {
        return p;
      }

      public Type visit(TypeAuto p, Context ctx) {
        return freshTypeVar();
      }

      @Override
      public Type visit(TypeForAll p, Context ctx) {
        ctx.enterTypeVarScope();
        for (String typeVar : p.liststellaident_) {
          ctx.addTypeVariable(typeVar);
        }
        p.type_.accept(
                this, ctx); // Проверяем внутренний тип с новыми переменными
        ctx.popTypeVarScope();
        return p;
      }

      public Type visit(TypeTop p, Context ctx) {
        return p;
      }

      public Type visit(TypeBottom p, Context ctx) {
        return p;
      }

      public Type visit(TypeRef p, Context ctx) {
        p.type_.accept(this, ctx);
        return p;
      }

      public Type visit(TypeVar p, Context ctx) {
        if (p.stellaident_.equals("auto")) {
          return freshTypeVar();
        }
        // для auto
        if (p.stellaident_.startsWith("?T")) return p;
        // дженерики
        if (!ctx.isTypeVarDefined(p.stellaident_)) {
          throw new TypeCheckException(
                  TypeCheckException.ErrorType.ERROR_UNDEFINED_TYPE_VARIABLE,
                  "Undefined type variable: " + p.stellaident_
          );
        }
        return p;
      }
    }

    public class MatchCaseVisitor implements MatchCase.Visitor<Type, Context> {
      public Type visit(
              AMatchCase p,
              Context ctx) { /* Code for AMatchCase goes here */
        p.pattern_.accept(new PatternVisitor(), ctx);
        p.expr_.accept(new ExprVisitor(), ctx);
        return null;
      }
    }

    public class OptionalTypingVisitor implements OptionalTyping.Visitor<Type, Context> {
      public Type visit(
              NoTyping p,
              Context ctx) {
        return new TypeUnit();
      }

      public Type visit(
              SomeTyping p,
              Context ctx) {
        p.type_.accept(new TypeVisitor(), ctx);
        return p.type_;
      }
    }

    public class PatternDataVisitor implements PatternData.Visitor<Type, Context> {
      public Type visit(
              NoPatternData p,
              Context ctx) { /* Code for NoPatternData goes here */
        return null;
      }

      public Type visit(
              SomePatternData p,
              Context ctx) { /* Code for SomePatternData goes here */
        p.pattern_.accept(new PatternVisitor(), ctx);
        return null;
      }
    }

    public class ExprDataVisitor implements ExprData.Visitor<Type, Context> {
      public Type visit(
              NoExprData p,
              Context ctx) { /* Code for NoExprData goes here */
        return null;
      }

      public Type visit(
              SomeExprData p,
              Context ctx) { /* Code for SomeExprData goes here */
        p.expr_.accept(new ExprVisitor(), ctx);
        return null;
      }
    }

    public class PatternVisitor implements Pattern.Visitor<Type, Context> {
      public Type visit(
              PatternCastAs p,
              Context ctx) { /* Code for PatternCastAs goes here */
        p.pattern_.accept(new PatternVisitor(), ctx);
        p.type_.accept(new TypeVisitor(), ctx);
        return null;
      }

      public Type visit(
              PatternAsc p,
              Context ctx) { /* Code for PatternAsc goes here */
        p.pattern_.accept(new PatternVisitor(), ctx);
        p.type_.accept(new TypeVisitor(), ctx);
        return null;
      }

      @Override
      public Type visit(PatternVariant p, Context ctx) {
        Type expectedType = ctx.getCurrentExpectedType();

        if (!(expectedType instanceof TypeVariant typeVariant)) {
          throw new TypeCheckException(
                  TypeCheckException.ErrorType.ERROR_UNEXPECTED_PATTERN_FOR_TYPE,
                  "Expected variant type, but got: " + TypePretty.pretty(
                          expectedType)
          );
        }

        AVariantFieldType field =
                getAVariantFieldType(p.stellaident_, typeVariant).orElseThrow(
                        () -> new TypeCheckException(
                                TypeCheckException.ErrorType.ERROR_UNEXPECTED_PATTERN_FOR_TYPE,
                                "Label '" + p.stellaident_ + "' is not defined in expected type "
                                        + TypePretty.pretty(typeVariant)
                        ));


        Type fieldContentType =
                field.optionaltyping_.accept(new OptionalTypingVisitor(), ctx);

        if (p.patterndata_ instanceof SomePatternData somePat) {
          ctx.pushExpectedType(fieldContentType);
          somePat.accept(new PatternDataVisitor(), ctx);
          ctx.popExpectedType();
        } else {
          unify(
                  fieldContentType,
                  new TypeUnit(), ctx
          );
        }

        return expectedType;
      }

      public Type visit(
              PatternInl p,
              Context ctx) { /* Code for PatternInl goes here */
        Type currentExpected = ctx.getCurrentExpectedType();
        if (!(currentExpected instanceof TypeSum sumType)) {
          throw new TypeCheckException(
                  TypeCheckException.ErrorType.ERROR_UNEXPECTED_PATTERN_FOR_TYPE,
                  "Pattern 'inl' requires a Sum type, but got: " + TypePretty.pretty(
                          currentExpected)
          );
        }
        ctx.pushExpectedType(sumType.type_1);
        p.pattern_.accept(this, ctx);
        ctx.popExpectedType();
        return sumType;
      }

      public Type visit(
              PatternInr p,
              Context ctx) { /* Code for PatternInr goes here */
        Type currentExpected = ctx.getCurrentExpectedType();
        if (!(currentExpected instanceof TypeSum sumType)) {
          throw new TypeCheckException(
                  TypeCheckException.ErrorType.ERROR_UNEXPECTED_PATTERN_FOR_TYPE,
                  "Pattern 'inr' requires a Sum type, but got: " + TypePretty.pretty(
                          currentExpected)
          );
        }
        ctx.pushExpectedType(sumType.type_2);
        p.pattern_.accept(this, ctx);
        ctx.popExpectedType();
        return sumType;
      }

      public Type visit(
              PatternTuple p,
              Context ctx) { /* Code for PatternTuple goes here */
        for (Pattern x : p.listpattern_) {
          x.accept(new PatternVisitor(), ctx);
        }
        return null;
      }

      public Type visit(
              PatternRecord p,
              Context ctx) { /* Code for PatternRecord goes here */
        for (LabelledPattern x : p.listlabelledpattern_) {
          x.accept(new LabelledPatternVisitor(), ctx);
        }
        return null;
      }

      public Type visit(
              PatternList p,
              Context ctx) { /* Code for PatternList goes here */
        for (Pattern x : p.listpattern_) {
          x.accept(new PatternVisitor(), ctx);
        }
        return null;
      }

      public Type visit(
              PatternCons p,
              Context ctx) { /* Code for PatternCons goes here */
        p.pattern_1.accept(new PatternVisitor(), ctx);
        p.pattern_2.accept(new PatternVisitor(), ctx);
        return null;
      }

      public Type visit(
              PatternFalse p,
              Context ctx) { /* Code for PatternFalse goes here */
        return null;
      }

      public Type visit(
              PatternTrue p,
              Context ctx) { /* Code for PatternTrue goes here */
        return null;
      }

      public Type visit(
              PatternUnit p,
              Context ctx) { /* Code for PatternUnit goes here */
        return null;
      }

      public Type visit(
              PatternInt p,
              Context ctx) { /* Code for PatternInt goes here */

        return null;
      }

      public Type visit(
              PatternSucc p,
              Context ctx) { /* Code for PatternSucc goes here */
        p.pattern_.accept(new PatternVisitor(), ctx);
        return null;
      }

      public Type visit(
              PatternVar p,
              Context ctx) { /* Code for PatternVar goes here */
        Type currentExpected = ctx.getCurrentExpectedType();
        if (currentExpected == null) {
          throw new TypeCheckException(
                  TypeCheckException.ErrorType.ERROR_UNEXPECTED_PATTERN_FOR_TYPE,
                  "Cannot infer type for pattern variable " + p.stellaident_
          );
        }
        String varName = p.stellaident_;
        ctx.addVariable(varName, currentExpected);
        return currentExpected;
      }
    }

    // ------------------
    public class LabelledPatternVisitor implements LabelledPattern.Visitor<Type, Context> {
      public Type visit(
              ALabelledPattern p,
              Context ctx) { /* Code for ALabelledPattern goes here */

        p.pattern_.accept(new PatternVisitor(), ctx);
        return null;
      }
    }

    public class BindingVisitor implements Binding.Visitor<Type, Context> {
      public Type visit(
              ABinding p,
              Context ctx) { /* Code for ABinding goes here */

        return p.expr_.accept(new ExprVisitor(), ctx);
      }
    }

    public class ExprVisitor implements Expr.Visitor<Type, Context> {
      public Type visit(
              Sequence p,
              Context ctx) { /* Code for Sequence goes here */
        ctx.pushExpectedType(new TypeUnit());
        Type t1 = p.expr_1.accept(this, ctx);
        ctx.popExpectedType();

        unify(new TypeUnit(), t1, ctx);

        Type expectedForSeq = ctx.getCurrentExpectedType();

        ctx.pushExpectedType(expectedForSeq);
        Type t2 = p.expr_2.accept(this, ctx);
        ctx.popExpectedType();

        return t2;
      }

      public Type visit(
              Let p,
              Context ctx) { /* Code for Let goes here */
        ctx.enterScope();
        for (PatternBinding x : p.listpatternbinding_) {
          x.accept(new PatternBindingVisitor(), ctx);
        }
        Type bodyType = p.expr_.accept(new ExprVisitor(), ctx);
        ctx.exitScope();
        return bodyType;
      }

      // TODO: нужно?
      public Type visit(
              LetRec p,
              Context ctx) { /* Code for LetRec goes here */
        for (PatternBinding x : p.listpatternbinding_) {
          x.accept(new PatternBindingVisitor(), ctx);
        }
        p.expr_.accept(new ExprVisitor(), ctx);
        return null;
      }

      // its fot Genretics
      public Type visit(TypeAbstraction p, Context ctx) {
        ctx.enterTypeVarScope();
        for (String typeVar : p.liststellaident_) {
          ctx.addTypeVariable(typeVar);
        }

        ctx.pushExpectedType(null);
        Type innerType = p.expr_.accept(this, ctx);
        ctx.popExpectedType();

        ctx.popTypeVarScope();

        org.syntax.stella.Absyn.ListStellaIdent typeVars =
                new org.syntax.stella.Absyn.ListStellaIdent();
        typeVars.addAll(p.liststellaident_);

        Type result = new TypeForAll(typeVars, innerType);

        Type expected = ctx.getCurrentExpectedType();
        if (expected != null) {
          unify(expected, result, ctx);
        }

        return result;
      }

      public Type visit(Assign p, Context ctx) {
        ctx.pushExpectedType(null);
        Type lhsType = p.expr_1.accept(this, ctx);
        ctx.popExpectedType();

        if (!(lhsType instanceof TypeRef tr)) {
          throw new TypeCheckException(
                  TypeCheckException.ErrorType.ERROR_NOT_A_REFERENCE,
                  "Left side of assignment must be a reference, but got: " + TypePretty.pretty(
                          lhsType)
          );
        }

        Type expectedValueType = tr.type_;
        ctx.pushExpectedType(expectedValueType);
        Type rhsType = p.expr_2.accept(this, ctx);
        ctx.popExpectedType();

        unify(expectedValueType, rhsType, ctx);

        return new TypeUnit();
      }

      public Type visit(If p, Context ctx) {
        ctx.pushExpectedType(new TypeBool());
        Type t1 = p.expr_1.accept(this, ctx);
        ctx.popExpectedType();

        checkThatTypeIsBool(t1);

        Type expected = ctx.getCurrentExpectedType();
        /**
         * Если прокинули ожидаемый тип, то используем его для обеих веток
         * иначе просто берем тип из первой ветки
         */
        ctx.pushExpectedType(expected);
        Type t2 = p.expr_2.accept(this, ctx);
        ctx.popExpectedType();

        ctx.pushExpectedType(expected != null ? expected : t2);
        Type t3 = p.expr_3.accept(this, ctx);
        ctx.popExpectedType();

        unify(t2, t3, ctx);

        return t2;
      }

      private void checkThatTypeIsBool(Type t1) {
        if (!(t1 instanceof TypeBool)) {
          throw new TypeCheckException(
                  TypeCheckException.ErrorType.ERROR_UNEXPECTED_TYPE_FOR_EXPRESSION,
                  new TypeBool(), t1
          );
        }
      }

      public Type visit(
              LessThan p,
              Context ctx) { /* Code for LessThan goes here */
        p.expr_1.accept(new ExprVisitor(), ctx);
        p.expr_2.accept(new ExprVisitor(), ctx);
        return null;
      }

      public Type visit(
              LessThanOrEqual p,
              Context ctx) { /* Code for LessThanOrEqual goes here */
        p.expr_1.accept(new ExprVisitor(), ctx);
        p.expr_2.accept(new ExprVisitor(), ctx);
        return null;
      }

      public Type visit(
              GreaterThan p,
              Context ctx) { /* Code for GreaterThan goes here */
        p.expr_1.accept(new ExprVisitor(), ctx);
        p.expr_2.accept(new ExprVisitor(), ctx);
        return null;
      }

      public Type visit(
              GreaterThanOrEqual p,
              Context ctx) { /* Code for GreaterThanOrEqual goes here */
        p.expr_1.accept(new ExprVisitor(), ctx);
        p.expr_2.accept(new ExprVisitor(), ctx);
        return null;
      }

      public Type visit(
              Equal p,
              Context ctx) { /* Code for Equal goes here */
        p.expr_1.accept(new ExprVisitor(), ctx);
        p.expr_2.accept(new ExprVisitor(), ctx);
        return null;
      }

      public Type visit(
              NotEqual p,
              Context ctx) { /* Code for NotEqual goes here */
        p.expr_1.accept(new ExprVisitor(), ctx);
        p.expr_2.accept(new ExprVisitor(), ctx);
        return null;
      }

      /**
       * <expr>  as <type>
       *
       * @param p
       * @param ctx
       * @return
       */
      public Type visit(
              TypeAsc p,
              Context ctx) { /* Code for TypeAsc goes here */
        var expectedType = p.type_.accept(new TypeVisitor(), ctx);
        ctx.pushExpectedType(expectedType);
        var actualType = p.expr_.accept(new ExprVisitor(), ctx);
        ctx.popExpectedType();
        unify(expectedType, actualType, ctx);
        return expectedType;
      }

      public Type visit(
              TypeCast p,
              Context ctx) {
        // 1. Проверяем выражение внутри каста (ожидаемый тип неизвестен, поэтому null)
        ctx.pushExpectedType(null);
        p.expr_.accept(this, ctx);
        ctx.popExpectedType();

        // 2. Получаем тип, к которому кастуем
        Type targetType = p.type_.accept(new TypeVisitor(), ctx);

        // 3. Возвращаем целевой тип
        return targetType;
      }

      /**
       * @param p   - Abstraction expression with One parameter
       * @param ctx
       * @return TODO: чекнуть работу
       */
      public Type visit(Abstraction p, Context ctx) {
        Type expected = ctx.getCurrentExpectedType();
        checkThatExpectedTypeIsFunction(expected);
        AParamDecl declaredParam = (AParamDecl) p.listparamdecl_.getFirst();
        Type expectedReturnType = null;

        if (expected instanceof TypeFun ef) {
          Type expectedParamType = ef.listtype_.get(0);
          checkLambdaParameters(expectedParamType, declaredParam.type_, ctx);
          expectedReturnType = ef.type_;
        }

        ctx.pushExpectedType(expectedReturnType);
        ctx.enterScope();
        ctx.addVariable(declaredParam.stellaident_, declaredParam.type_);

        Type actualBodyType = p.expr_.accept(this, ctx);

        ctx.exitScope();
        ctx.popExpectedType();

        ListType lt = new ListType();
        lt.add(declaredParam.type_);
        return new TypeFun(lt, actualBodyType);
      }

      private void checkLambdaParameters(
              Type expectedParamType, Type param, Context ctx) {
        if (ctx.isSubtypingEnabled()) {
          unify(param, expectedParamType, ctx);
        } else {
          if (!isSameType(param, expectedParamType)) {
            throw new TypeCheckException(
                    TypeCheckException.ErrorType.ERROR_UNEXPECTED_TYPE_FOR_PARAMETER,
                    expectedParamType, param
            );
          }
        }
      }

      private void checkThatExpectedTypeIsFunction(Type expected) {
        if (expected != null && !(expected instanceof TypeFun)) {
          throw new TypeCheckException(
                  TypeCheckException.ErrorType.ERROR_UNEXPECTED_LAMBDA,
                  "Unexpected lambda: expected a function type in this context, but the expected type is "
                          + TypePretty.pretty(expected) + "."
          );
        }
      }

      @Override
      public Type visit(Variant p, Context ctx) {
        checkForAmbiguousVariantType(p, ctx.getCurrentExpectedType());
        TypeVariant typeVariant = checkThatWeExpectVariantTypeFromAbove(ctx);

        AVariantFieldType field =
                getAVariantFieldType(p.stellaident_, typeVariant).orElseThrow(
                        () -> new TypeCheckException(
                                TypeCheckException.ErrorType.ERROR_UNEXPECTED_VARIANT_LABEL,
                                "Label '" + p.stellaident_ + "' is not defined in expected type "
                        ));
        Type fieldType =
                field.optionaltyping_.accept(new OptionalTypingVisitor(), ctx);

        if (p.exprdata_ instanceof SomeExprData data) {
          ctx.pushExpectedType(fieldType);
          Type actualType = data.expr_.accept(this, ctx);
          ctx.popExpectedType();
          unify(fieldType, actualType, ctx);
        } else {
          unify(
                  fieldType, new TypeUnit(),
                  ctx
          );
        }

        return typeVariant;
      }


      public TypeVariant checkThatWeExpectVariantTypeFromAbove(Context ctx) {
        if (!(ctx.getCurrentExpectedType() instanceof TypeVariant typeVariant)) {
          throw new TypeCheckException(
                  TypeCheckException.ErrorType.ERROR_UNEXPECTED_VARIANT,
                  "Expected type " + TypePretty.pretty(
                          ctx.getCurrentExpectedType())
                          + " but found variant construction."
          );
        }
        return typeVariant;
      }

      public void checkForAmbiguousVariantType(Variant p, Type expected) {
        if (expected == null) {
          throw new TypeCheckException(
                  TypeCheckException.ErrorType.ERROR_AMBIGUOUS_VARIANT_TYPE,
                  "Cannot infer type for variant <| " + p.stellaident_ + " ... |>. " +
                          "Expected type is missing."
          );
        }
      }

      /**
       * pattern должен совпадать с inputType
       * далее 2 варика что может вернуть тело кейса:
       * 1. Нам прокинули сверху
       * 2. Мы уже вывели тип из предыдущих кейсов
       */
      @Override
      public Type visit(Match p, Context ctx) {
        // обнуляем для кейса
        /**
         * fn f() -> Bool {
         *   return match (10) { ... } // (1)
         * }
         */
        ctx.pushExpectedType(null);
        Type inputType = p.expr_.accept(this, ctx);
        ctx.popExpectedType();

        checkThatMatchCasesExist(p);

        boolean checkedInl = false;
        boolean checkedInr = false;
        boolean checkedVar = false;
        Set<String> matchedVariantLabels = new HashSet<>();
        Type outerExpected = ctx.getCurrentExpectedType();
        Type inferredReturnType = null;

        for (MatchCase matchCase : p.listmatchcase_) {
          AMatchCase c =
                  (AMatchCase) matchCase;

          if (c.pattern_ instanceof PatternInl) {
            checkedInl = true;
          } else if (c.pattern_ instanceof PatternInr) {
            checkedInr = true;
          } else if (c.pattern_ instanceof PatternVar) {
            checkedVar = true;
          } else if (c.pattern_ instanceof PatternVariant variantPattern) {
            matchedVariantLabels.add(variantPattern.stellaident_);
          }

          ctx.enterScope();
          ctx.pushExpectedType(inputType);
          c.pattern_.accept(new PatternVisitor(), ctx);
          ctx.popExpectedType();
/**
 * если нас сверху прокинули ожидаемый тип, то используем его для всех кейсов
 * иначе для каждого кейса используем тип, который вывели из предыдущих кейсов
 */
          Type expectedForCase;
          if (outerExpected == null) {
            expectedForCase = inferredReturnType;
          } else expectedForCase = outerExpected;

          Type bodyType;

          if (expectedForCase != null) {
            ctx.pushExpectedType(expectedForCase);
            bodyType = c.expr_.accept(this, ctx);
            ctx.popExpectedType();
            unify(expectedForCase, bodyType, ctx);
          } else {
            bodyType = c.expr_.accept(this, ctx);
            inferredReturnType = bodyType;
          }

          ctx.exitScope();
        }

        checkForExhaustiveMatch(
                inputType, checkedVar, checkedInl, checkedInr
                , matchedVariantLabels
        );

        return outerExpected != null ? outerExpected : inferredReturnType;
      }

      private void checkForExhaustiveMatch(
              Type inputType, boolean checkedVar, boolean checkedInl,
              boolean checkedInr, Set<String> matchedVariantLabels) {
        if (inputType instanceof TypeSum) {
          if (!checkedVar && (!checkedInl || !checkedInr)) {
            throw new TypeCheckException(
                    TypeCheckException.ErrorType.ERROR_NONEXHAUSTIVE_MATCH_PATTERNS,
                    "Non-exhaustive patterns in match expression. " +
                            "Missing: " + (!checkedInl ? "inl " : "") + (!checkedInr ? "inr" : "")
            );
          }
        } else if (inputType instanceof TypeVariant variant) {
          var missingLabels = variant.listvariantfieldtype_.stream()
                  .filter(t -> t instanceof AVariantFieldType)
                  .map(t -> ((AVariantFieldType) t).stellaident_)
                  .filter(label -> !matchedVariantLabels.contains(label))
                  .toList();

          if (!missingLabels.isEmpty()) {
            String missingCases = String.join(", ", missingLabels);

            String errorMessage = """
                                  Non-exhaustive patterns in match expression.
                                  Missing variants: [%s]
                                  Found in type: %s\
                                  """.formatted(
                    missingCases, TypePretty.pretty(inputType));

            throw new TypeCheckException(
                    TypeCheckException.ErrorType.ERROR_NONEXHAUSTIVE_MATCH_PATTERNS,
                    errorMessage
            );
          }
        }
      }

      private void checkThatMatchCasesExist(Match p) {
        if (p.listmatchcase_.isEmpty()) {
          throw new TypeCheckException(
                  TypeCheckException.ErrorType.ERROR_ILLEGAL_EMPTY_MATCHING,
                  "Match expression cannot be empty"
          );
        }
      }

      /**
       * пытаемся вывести тип элементов:
       * - если контекст ожидает List, то используем его
       * - иначе null, и будем выводить по элементам
       * - еслси список пустой и сверху пусто, то кидаем ошибку
       *
       * @param p
       * @param ctx
       * @return
       */
      @Override
      public Type visit(org.syntax.stella.Absyn.List p, Context ctx) {
        Type expectedElementType;

        expectedElementType = tryToInferListTypeFromAbove(ctx);

        expectedElementType =
                chekForAmbiguityOrReturnTypeBottom(p, ctx, expectedElementType);
        Type finalElementType = expectedElementType;

        for (Expr expr : p.listexpr_) {
          ctx.pushExpectedType(finalElementType);
          Type itemType = expr.accept(this, ctx);
          ctx.popExpectedType();

          if (finalElementType == null) {
            finalElementType = itemType;
          } else {
            unify(finalElementType, itemType, ctx);
          }
        }

        return new TypeList(finalElementType);
      }

      private Type chekForAmbiguityOrReturnTypeBottom(
              List p, Context ctx, Type expectedElementType) {
        if (p.listexpr_.isEmpty() && expectedElementType == null) {
          if (ctx.isAmbiguousAsBottom()) {
            expectedElementType = new TypeBottom();
          } else {
            throw new TypeCheckException(
                    TypeCheckException.ErrorType.ERROR_AMBIGUOUS_LIST_TYPE,
                    "Cannot infer type for empty list [] without context."
            );
          }
        }
        return expectedElementType;
      }

      private Type tryToInferListTypeFromAbove(Context ctx) {
        if (ctx.getCurrentExpectedType() instanceof TypeList tl) {
          return tl.type_;
        } else if (ctx.getCurrentExpectedType() != null) {
          throw new TypeCheckException(
                  TypeCheckException.ErrorType.ERROR_UNEXPECTED_LIST,
                  "Expected type " + TypePretty.pretty(
                          ctx.getCurrentExpectedType())
                          + " but found a list literal."
          );
        }
        return null;
      }


      public Type visit(
              Add p,
              Context ctx) { /* Code for Add goes here */
        var t1 = p.expr_1.accept(new ExprVisitor(), ctx);
        var t2 = p.expr_2.accept(new ExprVisitor(), ctx);
        checkThatTypeIsNat(t1);
        checkThatTypeIsNat(t2);

        return new TypeNat();
      }

      public Type visit(
              Subtract p,
              Context ctx) { /* Code for Subtract goes here */
        var t1 = p.expr_1.accept(new ExprVisitor(), ctx);
        var t2 = p.expr_2.accept(new ExprVisitor(), ctx);
        checkThatTypeIsNat(t1);
        checkThatTypeIsNat(t2);
        return new TypeNat();
      }

      public Type visit(
              LogicOr p,
              Context ctx) { /* Code for LogicOr goes here */
        var t1 = p.expr_1.accept(new ExprVisitor(), ctx);
        var t2 = p.expr_2.accept(new ExprVisitor(), ctx);
        checkThatTypeIsBool(t1);
        checkThatTypeIsBool(t2);
        return new TypeBool();
      }

      public Type visit(
              Multiply p,
              Context ctx) { /* Code for Multiply goes here */
        var t1 = p.expr_1.accept(new ExprVisitor(), ctx);
        var t2 = p.expr_2.accept(new ExprVisitor(), ctx);
        checkThatTypeIsNat(t1);
        checkThatTypeIsNat(t2);
        return new TypeNat();
      }

      public Type visit(
              Divide p,
              Context ctx) { /* Code for Divide goes here */
        var t1 = p.expr_1.accept(new ExprVisitor(), ctx);
        var t2 = p.expr_2.accept(new ExprVisitor(), ctx);
        checkThatTypeIsNat(t1);
        checkThatTypeIsNat(t2);
        return new TypeNat();
      }

      public Type visit(
              LogicAnd p,
              Context ctx) { /* Code for LogicAnd goes here */
        var t1 = p.expr_1.accept(new ExprVisitor(), ctx);
        var t2 = p.expr_2.accept(new ExprVisitor(), ctx);
        checkThatTypeIsBool(t1);
        checkThatTypeIsBool(t2);
        return new TypeBool();
      }

      public Type visit(
              Ref p,
              Context ctx) {
        Type expected = ctx.getCurrentExpectedType();
        Type expectedInner = null;

        if (expected != null) {
          if (expected instanceof TypeRef tr) {
            expectedInner = tr.type_;
          } else {
            throw new TypeCheckException(
                    TypeCheckException.ErrorType.ERROR_UNEXPECTED_REFERENCE,
                    "Expected a non-reference type but found 'new(...)'. Expected: " + TypePretty.pretty(
                            expected)
            );
          }
        }
        ctx.pushExpectedType(expectedInner);
        Type innerType = p.expr_.accept(this, ctx);
        ctx.popExpectedType();

        return new TypeRef(innerType);
      }

      public Type visit(Deref p, Context ctx) {
        Type expected = ctx.getCurrentExpectedType();

        if (expected != null) {
          ctx.pushExpectedType(new TypeRef(expected));
        } else {
          ctx.pushExpectedType(null);
        }

        Type innerType = p.expr_.accept(this, ctx);
        ctx.popExpectedType();

        if (innerType instanceof TypeRef tr) {
          return tr.type_;
        } else {
          throw new TypeCheckException(
                  TypeCheckException.ErrorType.ERROR_NOT_A_REFERENCE,
                  "Attempt to dereference a non-reference type: " + TypePretty.pretty(
                          innerType)
          );
        }
      }

      public Type visit(Application p, Context ctx) {
        ctx.pushExpectedType(null);
        Type funType = p.expr_.accept(this, ctx);
        ctx.popExpectedType();

        Expr argExpr = p.listexpr_.getFirst();
        ctx.pushExpectedType(null);
        Type argType = argExpr.accept(this, ctx);
        ctx.popExpectedType();

        Type returnTypeVar = freshTypeVar();
        ListType expectedParamTypes = new ListType();
        expectedParamTypes.add(argType);
        Type expectedFunType = new TypeFun(expectedParamTypes, returnTypeVar);

        unify(funType, expectedFunType, ctx);
        return resolve(returnTypeVar);
      }


      public Type visit(TypeApplication p, Context ctx) {
        ctx.pushExpectedType(null);
        Type exprType = p.expr_.accept(this, ctx);
        ctx.popExpectedType();

        Type resolvedExprType = resolve(exprType);

        if (!(resolvedExprType instanceof TypeForAll tfa)) {
          throw new TypeCheckException(
                  TypeCheckException.ErrorType.ERROR_NOT_A_GENERIC_FUNCTION,
                  "Expected a generic function, but got: " + TypePretty.pretty(
                          resolvedExprType)
          );
        }

        Type resultType = tfa.type_;

        for (int i =
             0; i < tfa.liststellaident_.size() && i < p.listtype_.size(); i++) {
          String varName = tfa.liststellaident_.get(i);
          Type argType = p.listtype_.get(i);

          argType.accept(
                  new TypeVisitor(),
                  ctx
          );
          resultType = substitute(resultType, varName, argType);
        }

        Type expected = ctx.getCurrentExpectedType();
        if (expected != null) {
          unify(expected, resultType, ctx);
        }

        return resultType;
      }

      /**
       * нам дано expr, stellaident. От expr мы хотим получить TypeRecord ->
       * чекнуть что поле есть
       *
       * @param p
       * @param ctx
       * @return
       */
      public Type visit(DotRecord p, Context ctx) {
        ctx.pushExpectedType(null);
        Type leftType = p.expr_.accept(new ExprVisitor(), ctx);
        ctx.popExpectedType();
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
                  "Expected a record type but got: " + TypePretty.pretty(
                          leftType)
          );
        }
        return recordType;
      }

      public Type visit(
              DotTuple p,

              Context ctx) { /* Code for DotTuple goes here */
        ctx.pushExpectedType(null); // нужно?
        Type typeLeft = p.expr_.accept(new ExprVisitor(), ctx);
        ctx.popExpectedType();
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
                  "Expected a tuple type but got: " + TypePretty.pretty(
                          typeLeft)
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

      public Type visit(Tuple p, Context ctx) {
        Optional<TypeTuple> expectedTuple = Optional.empty();
        if (ctx.getCurrentExpectedType() != null) {
          if (ctx.getCurrentExpectedType() instanceof TypeTuple) {
            expectedTuple = getExpectedTuple(p, ctx);
          } else {
            throw new TypeCheckException(
                    TypeCheckException.ErrorType.ERROR_UNEXPECTED_TUPLE,
                    "Expected " + TypePretty.pretty(
                            ctx.getCurrentExpectedType()) + " but got a tuple."
            );
          }
        }
        ListType componentTypes = new ListType();
        for (int i = 0; i < p.listexpr_.size(); i++) {
          Expr expr = p.listexpr_.get(i);

          /**
           * если у нас есть ожидаемый тип кортежа сверху,
           * то для соотв элемента мы прокидываем ожидаемый тип
           *
           */
          if (expectedTuple.isPresent()) {
            ctx.pushExpectedType(expectedTuple.get().listtype_.get(i));
          } else {
            ctx.pushExpectedType(null);
          }

          Type actualType = expr.accept(this, ctx);
          componentTypes.add(actualType);

          ctx.popExpectedType();
        }
        return new TypeTuple(componentTypes);
      }

      private Optional<TypeTuple> getExpectedTuple(Tuple p, Context ctx) {
        Optional<TypeTuple> expectedTuple;
        expectedTuple = validateTuple(p, ctx);
        return expectedTuple;
      }

      private Optional<TypeTuple> validateTuple(Tuple p, Context ctx) {
        Optional<TypeTuple> expectedTuple;
        if (ctx.getCurrentExpectedType() instanceof TypeTuple et) {
          expectedTuple = Optional.of(et);
          ListType expectedComponentTypes = et.listtype_;
          if (expectedComponentTypes.size() != p.listexpr_.size()) {
            throw new TypeCheckException(
                    TypeCheckException.ErrorType.ERROR_UNEXPECTED_TUPLE_LENGTH,
                    "Expected tuple of size " + expectedComponentTypes.size() +
                            " but got tuple of size " + p.listexpr_.size()
            );
          }
        } else
          throw new TypeCheckException(
                  TypeCheckException.ErrorType.ERROR_UNEXPECTED_TUPLE,
                  "Expected " + ctx.getCurrentExpectedType() + " but got a tuple."
          );
        return expectedTuple;
      }

      /**
       * если нам прокинули сверху TypeRecord, то для каждого поля мы прокидываем ожидаемый тип
       *
       * @param p
       * @param ctx
       * @return
       */
      public Type visit(Record p, Context ctx) {
        ListRecordFieldType fieldTypes = new ListRecordFieldType();
        Set<String> seenFields = new HashSet<>();

        TypeRecord expectedRecord = null;
        if (ctx.getCurrentExpectedType() != null) {
          expectedRecord = chekIfExprectedTypeIsRecord(ctx);
        }

        for (Binding binding : p.listbinding_) {
          ABinding b = (ABinding) binding;

          if (seenFields.contains(b.stellaident_)) {
            throw new TypeCheckException(
                    TypeCheckException.ErrorType.ERROR_DUPLICATE_RECORD_FIELDS,
                    "Duplicate field '" + b.stellaident_ + "' in record construction"
            );
          }
          seenFields.add(b.stellaident_);

          Optional<Type> expectedFieldTypeOpt = (expectedRecord != null)
                  ? tryFindFieldTypeForRecord(expectedRecord, b.stellaident_)
                  : Optional.empty();

          if (expectedRecord != null && expectedFieldTypeOpt.isEmpty() && !ctx.isSubtypingEnabled()) {
            throw new TypeCheckException(
                    TypeCheckException.ErrorType.ERROR_UNEXPECTED_RECORD_FIELDS,
                    "Unexpected field '" + b.stellaident_ + "' in record construction."
            );
          }

          Type expectedFieldType = expectedFieldTypeOpt.orElse(null);
          ctx.pushExpectedType(expectedFieldType);
          Type fieldType = b.expr_.accept(new ExprVisitor(), ctx);
          ctx.popExpectedType();

          if (expectedFieldType != null) {
            unify(expectedFieldType, fieldType, ctx);
          }

          fieldTypes.add(new ARecordFieldType(b.stellaident_, fieldType));
        }

        TypeRecord actualType = new TypeRecord(fieldTypes);
        if (expectedRecord != null) {
          checkRecordMismatch(expectedRecord, actualType, ctx);
        }

        return actualType;
      }

      private TypeRecord chekIfExprectedTypeIsRecord(Context ctx) {
        TypeRecord expectedRecord;
        if (ctx.getCurrentExpectedType() instanceof TypeRecord tr) {
          expectedRecord = tr;
        } else {
          throw new TypeCheckException(
                  TypeCheckException.ErrorType.ERROR_UNEXPECTED_RECORD,
                  "Expected type " + TypePretty.pretty(
                          ctx.getCurrentExpectedType())
                          + " but found a record."
          );
        }
        return expectedRecord;
      }

      private Optional<Type> tryFindFieldTypeForRecord(
              TypeRecord expectedRecord, String stellaident_) {
        for (RecordFieldType f : expectedRecord.listrecordfieldtype_) {
          if (f instanceof ARecordFieldType arft) {
            if (arft.stellaident_.equals(stellaident_)) {
              return Optional.of(arft.type_);
            }
          }
        }
        return Optional.empty();
      }

      public TypeList checkThatTypeIsList(Type type) {
        if (!(type instanceof TypeList listType)) {
          throw new TypeCheckException(
                  TypeCheckException.ErrorType.ERROR_NOT_A_LIST,
                  "Expected a list type but got: " + TypePretty.pretty(type)
          );
        }
        return listType;
      }

      /**
       * Γ ` t1 : T1 Γ ` t2 : List T1
       * Γ ` cons[T1] t1 t2 : List T1
       *
       * @param p
       * @param ctx
       * @return
       */
      @Override
      public Type visit(ConsList p, Context ctx) {
        Type expectedElementType = null;

        expectedElementType =
                tryToGetElementTypeFromAbove(ctx, expectedElementType);

        Type headType = checkHead(p, ctx, expectedElementType);

        if (expectedElementType == null) {
          expectedElementType = headType;
        } else {
          unify(expectedElementType, headType, ctx);
        }

        TypeList expectedTailType =
                new TypeList(expectedElementType);

        Type tailType = checkTail(p, ctx, expectedTailType);

        unify(expectedTailType, tailType, ctx);

        return expectedTailType;
      }

      private Type tryToGetElementTypeFromAbove(
              Context ctx, Type expectedElementType) {
        if (ctx.getCurrentExpectedType() instanceof TypeList tl) {
          expectedElementType = tl.type_;
        } else if (ctx.getCurrentExpectedType() != null) {
          throw new TypeCheckException(
                  TypeCheckException.ErrorType.ERROR_UNEXPECTED_LIST,
                  "Expected type " + TypePretty.pretty(
                          ctx.getCurrentExpectedType())
                          + " but found cons construction."
          );
        }
        return expectedElementType;
      }

      private Type checkTail(
              ConsList p, Context ctx, TypeList expectedTailType) {
        ctx.pushExpectedType(expectedTailType);
        Type tailType = p.expr_2.accept(this, ctx);
        ctx.popExpectedType();
        return tailType;
      }

      private Type checkHead(
              ConsList p, Context ctx, Type expectedElementType) {
        ctx.pushExpectedType(expectedElementType);
        Type headType = p.expr_1.accept(this, ctx);
        ctx.popExpectedType();
        return headType;
      }


      @Override
      public Type visit(Head p, Context ctx) {
        ctx.pushExpectedType(null);
        Type listExprType = p.expr_.accept(this, ctx);
        ctx.popExpectedType();

        TypeList listType =
                checkThatTypeIsList(listExprType);

        return listType.type_;
      }

      @Override
      public Type visit(IsEmpty p, Context ctx) {
        ctx.pushExpectedType(null);
        Type listExprType = p.expr_.accept(this, ctx);
        ctx.popExpectedType();

        checkThatTypeIsList(listExprType);

        return new TypeBool();
      }

      @Override
      public Type visit(Tail p, Context ctx) {
        ctx.pushExpectedType(null);
        Type listExprType = p.expr_.accept(this, ctx);
        ctx.popExpectedType();

        return checkThatTypeIsList(listExprType);
      }

      public Type visit(Panic p, Context ctx) {
        Type expected = ctx.getCurrentExpectedType();

        if (expected != null) {
          return expected;
        }

        if (ctx.isAmbiguousAsBottom()) {
          return new TypeBottom();
        }

        throw new TypeCheckException(
                TypeCheckException.ErrorType.ERROR_AMBIGUOUS_PANIC_TYPE,
                "Cannot infer type for 'panic!' without context."
        );
      }

      public Type visit(Throw p, Context ctx) {
        Type expected = ctx.getCurrentExpectedType();

        Type exceptionType = ctx.getExceptionType()
                .orElseThrow(() -> new TypeCheckException(
                        TypeCheckException.ErrorType.ERROR_EXCEPTION_TYPE_NOT_DECLARED,
                        "The program uses exceptions but their type is not declared."
                ));
        ctx.pushExpectedType(exceptionType);
        p.expr_.accept(new ExprVisitor(), ctx);
        ctx.popExpectedType();

        if (expected == null) {
          if (ctx.isAmbiguousAsBottom()) {
            return new TypeBottom();
          }
          throw new TypeCheckException(
                  TypeCheckException.ErrorType.ERROR_AMBIGUOUS_THROW_TYPE,
                  "Ambiguous type of a throw expression."
          );
        }

        return expected;
      }

      public Type visit(TryCatch p, Context ctx) {
        Type tryType = p.expr_1.accept(new ExprVisitor(), ctx);

        Type exceptionType = ctx.getExceptionType()
                .orElseThrow(() -> new TypeCheckException(
                        TypeCheckException.ErrorType.ERROR_EXCEPTION_TYPE_NOT_DECLARED,
                        "The program uses exceptions but their type is not declared."
                ));

        ctx.enterScope();

        ctx.pushExpectedType(exceptionType);
        p.pattern_.accept(new PatternVisitor(), ctx);
        ctx.popExpectedType();

        ctx.pushExpectedType(tryType);
        Type accept = p.expr_2.accept(new ExprVisitor(), ctx);
        ctx.popExpectedType();

        unify(tryType, accept, ctx);

        ctx.exitScope();
        return tryType;
      }

      public Type visit(TryWith p, Context ctx) {
        Type tryType = p.expr_1.accept(new ExprVisitor(), ctx);

        ctx.pushExpectedType(tryType);
        Type accept = p.expr_2.accept(new ExprVisitor(), ctx);
        ctx.popExpectedType();

        unify(tryType, accept, ctx);
        return tryType;
      }

      public Type visit(
              TryCastAs p,
              Context ctx) { /* Code for TryCastAs goes here */
        p.expr_1.accept(new ExprVisitor(), ctx);
        p.type_.accept(new TypeVisitor(), ctx);
        p.pattern_.accept(new PatternVisitor(), ctx);
        p.expr_2.accept(new ExprVisitor(), ctx);
        p.expr_3.accept(new ExprVisitor(), ctx);
        return null;
      }

      public Type visit(
              Inl p,
              Context ctx) { /* Code for Inl goes here */
        Type currentExpected = ctx.getCurrentExpectedType();

        if (currentExpected == null) {
          if (ctx.isAmbiguousAsBottom()) {
            ctx.pushExpectedType(null);
            Type actualInner = p.expr_.accept(this, ctx);
            ctx.popExpectedType();

            return new TypeSum(actualInner, new TypeBottom());
          }

          throw new TypeCheckException(
                  TypeCheckException.ErrorType.ERROR_AMBIGUOUS_SUM_TYPE,
                  "Cannot infer type for 'inl' injection without context."
          );
        }

        if (!(currentExpected instanceof TypeSum ts)) {
          throw new TypeCheckException(
                  TypeCheckException.ErrorType.ERROR_UNEXPECTED_INJECTION,
                  "Expected " + TypePretty.pretty(
                          currentExpected) + " but found inl(...)"
          );
        }

        ctx.pushExpectedType(ts.type_1);
        Type actualInner = p.expr_.accept(this, ctx);
        ctx.popExpectedType();

        unify(ts.type_1, actualInner, ctx);

        return currentExpected;
      }

      public Type visit(
              Inr p,
              Context ctx) { /* Code for Inr goes here */
        Type currentExpected = ctx.getCurrentExpectedType();

        if (currentExpected == null) {
          if (ctx.isAmbiguousAsBottom()) {
            ctx.pushExpectedType(null);
            Type actualInner = p.expr_.accept(this, ctx);
            ctx.popExpectedType();
            return new TypeSum(new TypeBottom(), actualInner);
          }
          throw new TypeCheckException(
                  TypeCheckException.ErrorType.ERROR_AMBIGUOUS_SUM_TYPE,
                  "Cannot infer type for 'inr' without context."
          );
        }

        if (!(currentExpected instanceof TypeSum ts)) {
          throw new TypeCheckException(
                  TypeCheckException.ErrorType.ERROR_UNEXPECTED_INJECTION,
                  "Expected " + TypePretty.pretty(
                          currentExpected) + " but found inr(...)"
          );
        }

        ctx.pushExpectedType(ts.type_2);
        Type actualInner = p.expr_.accept(this, ctx);
        ctx.popExpectedType();
        unify(ts.type_2, actualInner, ctx);

        return currentExpected;
      }


      private void checkForAmbiguousSumType(Type currentExpected) {
        if (currentExpected == null) {
          throw new TypeCheckException(
                  TypeCheckException.ErrorType.ERROR_AMBIGUOUS_SUM_TYPE,
                  "Cannot infer type for 'inl' injection without context. " +
                          "Use type ascription: inl(...) as (Type1 + Type2)"
          );
        }
      }

      private TypeSum checkThatExpectSumType(Type currentExpected) {
        if (!(currentExpected instanceof TypeSum ts)) {
          throw new TypeCheckException(
                  TypeCheckException.ErrorType.ERROR_UNEXPECTED_INJECTION,
                  "Expected a sum type for Inl but got: " + TypePretty.pretty(
                          currentExpected)
          );
        }
        return ts;
      }


      public Type visit(
              Succ p,
              Context ctx) { /* Code for Succ goes here */
        var t1 = p.expr_.accept(new ExprVisitor(), ctx);
        checkThatTypeIsNat(t1);
        return new TypeNat();
      }

      public Type visit(
              LogicNot p,
              Context ctx) { /* Code for LogicNot goes here */
        var t1 = p.expr_.accept(new ExprVisitor(), ctx);
        checkThatTypeIsBool(t1);
        return new TypeBool();
      }

      public Type visit(
              Pred p,
              Context ctx) { /* Code for Pred goes here */
        var t1 = p.expr_.accept(new ExprVisitor(), ctx);
        checkThatTypeIsNat(t1);
        return new TypeNat();
      }

      public Type visit(
              IsZero p,
              Context ctx) { /* Code for IsZero goes here */
        var t1 = p.expr_.accept(new ExprVisitor(), ctx);
        checkThatTypeIsNat(t1);
        return new TypeBool();
      }

      // Γ ` t1 : T1→T1
      //---------------------
      //Γ ` fix t1 : T1
      public Type visit(Fix p, Context ctx) {
        /**
         * если нам сверху сказали, что fix должен вернуть тип T, то
         * функция, которую мы передаем в fix (expr), должна быть типа T -> T
         */
        Type expectedT = ctx.getCurrentExpectedType();
        if (expectedT != null) {
          ListType lt = new ListType();
          lt.add(expectedT);
          ctx.pushExpectedType(new TypeFun(lt, expectedT));
        }
        Type inferredType = p.expr_.accept(new ExprVisitor(), ctx);
        if (expectedT != null) {
          ctx.popExpectedType();
        }

        TypeFun funType = checkThatTypeIsTypeFun(inferredType);

        Type argType = funType.listtype_.getFirst();
        Type retType = funType.type_;

        unify(argType, retType, ctx);


        return argType;
      }

      private TypeFun checkThatTypeIsTypeFun(Type inferredType) {
        if (!(inferredType instanceof TypeFun funType)) {
          throw new TypeCheckException(
                  TypeCheckException.ErrorType.ERROR_NOT_A_FUNCTION,
                  "Expected a function type but got: " + TypePretty.pretty(
                          inferredType)
          );
        }
        return funType;
      }

      public Type visit(NatRec p, Context ctx) {
        ctx.pushExpectedType(new TypeNat());
        var t1 = p.expr_1.accept(new ExprVisitor(), ctx);
        ctx.popExpectedType();
        checkThatTypeIsNat(t1);

        var t2 = p.expr_2.accept(new ExprVisitor(), ctx);
        ListType innerParamTypes = new ListType();
        innerParamTypes.add(t2);
        TypeFun innerFunType = new TypeFun(innerParamTypes, t2);

        ListType outerParamTypes = new ListType();
        outerParamTypes.add(new TypeNat());
        Type expectedStepType = new TypeFun(outerParamTypes, innerFunType);


        ctx.pushExpectedType(expectedStepType);
        var t3 = p.expr_3.accept(new ExprVisitor(), ctx);
        ctx.popExpectedType();

        unify(expectedStepType, t3, ctx);

        return t2;
      }


      private void checkThatTypeIsNat(Type t1) {
        if (!(t1 instanceof TypeNat)) {
          throw new TypeCheckException(
                  TypeCheckException.ErrorType.ERROR_UNEXPECTED_TYPE_FOR_EXPRESSION,
                  new TypeNat(), t1
          );
        }
      }

      public Type visit(
              Fold p,
              Context ctx) { /* Code for Fold goes here */
        p.type_.accept(new TypeVisitor(), ctx);
        p.expr_.accept(new ExprVisitor(), ctx);
        return null;
      }

      public Type visit(
              Unfold p,
              Context ctx) { /* Code for Unfold goes here */
        p.type_.accept(new TypeVisitor(), ctx);
        p.expr_.accept(new ExprVisitor(), ctx);
        return null;
      }

      public Type visit(
              ConstTrue p,
              Context ctx) { /* Code for ConstTrue goes here */
        return new TypeBool();
      }

      public Type visit(
              ConstFalse p,
              Context ctx) { /* Code for ConstFalse goes here */
        return new TypeBool();
      }

      public Type visit(
              ConstUnit p,
              Context ctx) { /* Code for ConstUnit goes here */
        return new TypeUnit();
      }

      public Type visit(
              ConstInt p,
              Context ctx) { /* Code for ConstInt goes here */
        if (p.integer_ < 0) {
          throw new TypeCheckException(
                  TypeCheckException.ErrorType.ERROR_ILLEGAL_NEGATIVE_LITERAL,
                  "Negative literal " + p.integer_ + " is not allowed"
          );
        }
        return new TypeNat();
      }

      public Type visit(ConstMemory p, Context ctx) {
        Type expected = ctx.getCurrentExpectedType();

        if (expected == null) {
          if (ctx.isAmbiguousAsBottom()) {
            return new TypeRef(
                    new TypeBottom()); // или не
            // оборачивать?
          }
          throw new TypeCheckException(
                  TypeCheckException.ErrorType.ERROR_AMBIGUOUS_REFERENCE_TYPE,
                  "Cannot infer type of memory address without context."
          );
        }

        if (!(expected instanceof TypeRef)) {
          throw new TypeCheckException(
                  TypeCheckException.ErrorType.ERROR_UNEXPECTED_MEMORY_ADDRESS,
                  "A memory address was used where a non-reference type was expected. Expected: " + TypePretty.pretty(
                          expected)
          );
        }

        return expected;
      }

      public Type visit(
              Var p,
              Context ctx) { /* Code for Var goes here */
        return ctx.lookup(p.stellaident_)
                .orElseThrow(() -> new TypeCheckException(
                        TypeCheckException.ErrorType.ERROR_UNDEFINED_VARIABLE,
                        "Variable " + p.stellaident_ + "is not defined."
                ));
      }
    }

    public class PatternBindingVisitor implements PatternBinding.Visitor<Type, Context> {
      public Type visit(
              APatternBinding p,
              Context ctx) {

        // сначала infer правую часть (expr)
        ctx.pushExpectedType(null);
        Type exprType = p.expr_.accept(new ExprVisitor(), ctx);
        ctx.popExpectedType();
        // затем чекнем левую часть с этим типом
        ctx.pushExpectedType(exprType);
        p.pattern_.accept(new PatternVisitor(), ctx);
        ctx.popExpectedType();

        return null;
      }
    }

    public class VariantFieldTypeVisitor implements VariantFieldType.Visitor<Type, Context> {
      public Type visit(
              AVariantFieldType p, Context ctx) {
        return p.optionaltyping_.accept(new OptionalTypingVisitor(), ctx);
      }
    }

    public class RecordFieldTypeVisitor implements RecordFieldType.Visitor<Type, Context> {
      public Type visit(
              ARecordFieldType p,
              Context ctx) { /* Code for ARecordFieldType goes here */

        return p.type_.accept(new TypeVisitor(), ctx);
      }
    }

    public class TypingVisitor implements Typing.Visitor<Type, Context> {
      public Type visit(ATyping p, Context ctx) {
        Type expectedType = p.type_.accept(new TypeVisitor(), ctx);

        ctx.pushExpectedType(expectedType);

        Type actualType = p.expr_.accept(new ExprVisitor(), ctx);

        ctx.popExpectedType();

        unify(expectedType, actualType, ctx);

        return actualType;
      }
    }
  }
}