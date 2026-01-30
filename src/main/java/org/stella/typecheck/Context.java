package org.stella.typecheck;

import org.syntax.stella.Absyn.Type;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Optional;

public class Context {


  private LinkedList<HashMap<String, Type>> scopes = new LinkedList<>();



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