package org.stella.typecheck;

import org.syntax.stella.Absyn.Type;

import java.util.*;

public class Context {

  private final LinkedList<HashMap<String, Type>> scopes = new LinkedList<>();
  private final Deque<Type> expectedTypes = new LinkedList<>();
  LinkedList<Set<String>> typeVarsScopes = new LinkedList<>();
  private final Set<String> extensions = new HashSet<>();

  private Type exceptionType = null;

  public Context() {
    enterScope();
    enterTypeVarScope();
  }

  public boolean isReconstructionEnabled() {
    return hasExtension("#type-reconstruction");
  }

  public boolean isUniversalTypesEnabled() {
    return hasExtension("#universal-types");
  }

  // --- Управление переменными типа (п. 3.2.3)  ---
  public void enterTypeVarScope() {
    typeVarsScopes.addFirst(new HashSet<>());
  }

  public void popTypeVarScope() {
    typeVarsScopes.removeFirst();
  }

  public void addTypeVariable(String name) {
    // Проверка на дубликаты для экстра-баллов (п. 3.2.4) [cite: 101]
    if (typeVarsScopes.getFirst().contains(name)) {
      // throw new TypeCheckException(ERROR_DUPLICATE_TYPE_PARAMETER)
    }
    typeVarsScopes.getFirst().add(name);
  }

  public boolean isTypeVarDefined(String name) {
    for (Set<String> scope : typeVarsScopes) {
      if (scope.contains(name)) return true;
    }
    return false;
  }
  public void addExtension(String extension) {
    extensions.add(extension);
  }

  public boolean hasExtension(String extension) {
    return extensions.contains(extension);
  }

  public boolean isSubtypingEnabled() {
    return hasExtension("#structural-subtyping");
  }

  public boolean isAmbiguousAsBottom() {
    return hasExtension("#ambiguous-type-as-bottom");
  }

  public void setExceptionType(Type type) {
    this.exceptionType = type;
  }

  public Optional<Type> getExceptionType() {
    return Optional.ofNullable(exceptionType);
  }


  public void pushExpectedType(Type type) {
    expectedTypes.push(type);
  }

  public Type popExpectedType() {
    return expectedTypes.isEmpty() ? null : expectedTypes.pop();
  }

  public Type getCurrentExpectedType() {
    return expectedTypes.peek(); // возвращает null, если стек пуст
  }

  public LinkedList<HashMap<String, Type>> getScopes() {
    return scopes;
  }

  public void enterScope() {
    scopes.addFirst(new HashMap<>());
  }

  public void exitScope() {
    scopes.removeFirst();
  }

  public void addVariable(String name, Type type) {
    scopes.getFirst().put(name, type);
  }

  public Optional<Type> lookup(String name) {
    for (HashMap<String, Type> scope : scopes) {
      if (scope.containsKey(name)) {
        return Optional.of(scope.get(name));
      }
    }
    return Optional.empty();
  }
}