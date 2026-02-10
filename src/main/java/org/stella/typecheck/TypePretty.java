package org.stella.typecheck;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.syntax.stella.Absyn.*;

/**
 * Human-readable (short) string representation for Stella Absyn types.
 * Avoids default toString() which often prints full Java class names.
 */
public final class TypePretty {
  private TypePretty() {}

  public static String pretty(Type t) {
    if (t == null) return "<unknown>";

    // Simple atoms
    if (t instanceof TypeNat) return "Nat";
    if (t instanceof TypeBool) return "Bool";
    if (t instanceof TypeUnit) return "Unit";
    if (t instanceof TypeTop) return "Top";
    if (t instanceof TypeBottom) return "Bottom";
    if (t instanceof TypeAuto) return "Auto";

    // Variables / recursion
    if (t instanceof TypeVar tv) return tv.stellaident_;
    if (t instanceof TypeRec tr) return "Rec " + tr.stellaident_ + ". " + pretty(tr.type_);

    // Type constructors
    if (t instanceof TypeList tl) return "List[" + pretty(tl.type_) + "]";
    if (t instanceof TypeRef tr) return "Ref " + wrapIfFunOrSumOrForall(tr.type_);
    if (t instanceof TypeSum ts) return wrapIfFunOrForall(ts.type_1) + " + " + wrapIfFunOrForall(ts.type_2);

    if (t instanceof TypeTuple tt) {
      List<String> items = new ArrayList<>();
      for (Type x : tt.listtype_) items.add(pretty(x));
      return "(" + String.join(", ", items) + ")";
    }

    if (t instanceof TypeRecord tr) {
      List<String> fields = new ArrayList<>();
      for (RecordFieldType rf : tr.listrecordfieldtype_) {
        ARecordFieldType f = (ARecordFieldType) rf;
        fields.add(f.stellaident_ + ": " + pretty(f.type_));
      }
      return "{" + String.join(", ", fields) + "}";
    }

    if (t instanceof TypeVariant tv) {
      List<String> fields = new ArrayList<>();
      for (VariantFieldType vf : tv.listvariantfieldtype_) {
        AVariantFieldType f = (AVariantFieldType) vf;
        Type content = f.optionaltyping_.accept(new OptionalTyping.Visitor<Type, Void>() {
          @Override
          public Type visit(NoTyping p, Void arg) {
            return new TypeUnit();
          }

          @Override
          public Type visit(SomeTyping p, Void arg) {
            return p.type_;
          }
        }, null);
        fields.add(f.stellaident_ + ": " + pretty(content));
      }
      return "<| " + String.join(", ", fields) + " |>";
    }

    if (t instanceof TypeFun tf) {
      List<String> args = tf.listtype_.stream().map(TypePretty::wrapIfFunOrForall).collect(Collectors.toList());
      String argsStr = (args.size() == 1) ? args.get(0) : "(" + String.join(", ", args) + ")";
      return argsStr + " -> " + pretty(tf.type_);
    }

    if (t instanceof TypeForAll ta) {
      return "forall " + String.join(" ", ta.liststellaident_) + ". " + pretty(ta.type_);
    }

    // Fallback: last resort, but still avoid full package path when possible.
    return t.getClass().getSimpleName();
  }

  private static String wrapIfFunOrSumOrForall(Type t) {
    if (t instanceof TypeFun || t instanceof TypeSum || t instanceof TypeForAll) {
      return "(" + pretty(t) + ")";
    }
    return pretty(t);
  }

  private static String wrapIfFunOrForall(Type t) {
    if (t instanceof TypeFun || t instanceof TypeForAll) {
      return "(" + pretty(t) + ")";
    }
    return pretty(t);
  }
}

