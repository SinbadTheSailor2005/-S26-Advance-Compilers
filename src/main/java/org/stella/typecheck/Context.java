package org.stella.typecheck;

import org.syntax.stella.Absyn.Type;

import java.util.*;

public class Context {


  private final LinkedList<HashMap<String, Type>> scopes = new LinkedList<>();
  private final Deque<Type> expectedTypes = new ArrayDeque<>();

  public void pushExpectedType(Type type) {
    expectedTypes.push(type);
  }

  public Type popExpectedType() {
    return expectedTypes.isEmpty() ? null : expectedTypes.pop();
  }

  public Type getCurrentExpectedType() {
    return expectedTypes.peek(); // возвращает null, если стек пуст
  }

  public Context() {

    enterScope();
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
    scopes.getFirst()
            .put(name, type);
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