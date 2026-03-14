package org.stella;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;

public class AstDumper {
  public static void dump(Object o) {
    System.out.println("=== AST STRUCTURE ===");
    dump(o, 0);
    System.out.println("=====================");
  }

  private static void dump(Object o, int indent) {
    String pad = "  ".repeat(indent);

    if (o == null) {
      System.out.println(pad + "null");
      return;
    }

    // 1. Если это список (например, список аргументов или выражений)
    if (o instanceof List) {
      List<?> list = (List<?>) o;
      if (list.isEmpty()) {
        System.out.println(pad + "[]");
      } else {
        System.out.println(pad + "[");
        for (Object item : list) {
          dump(item, indent + 1);
        }
        System.out.println(pad + "]");
      }
      return;
    }

    // 2. Базовые типы (String, Integer, Boolean)
    if (o instanceof String || o instanceof Number || o instanceof Boolean) {
      System.out.println(pad + o);
      return;
    }

    // 3. Классы AST (BNFC)
    Class<?> clazz = o.getClass();
    // Печатаем название класса (например, "Succ", "NatRec", "DeclFun")
    System.out.println(pad + clazz.getSimpleName() + " {");

    // Бежим по всем полям класса
    for (Field field : clazz.getFields()) {
      // Пропускаем статические поля
      if (Modifier.isStatic(field.getModifiers())) continue;

      try {
        Object value = field.get(o);
        // Если значение null, пропускаем для чистоты (или можно выводить)
        if (value == null) continue;

        System.out.print(pad + "  " + field.getName() + ": ");

        // Красивый вывод значений
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
          System.out.println(value);
        } else {
          System.out.println(); // Перенос строки для вложенных объектов
          dump(value, indent + 2);
        }
      } catch (IllegalAccessException e) {
        e.printStackTrace();
      }
    }
    System.out.println(pad + "}");
  }
}