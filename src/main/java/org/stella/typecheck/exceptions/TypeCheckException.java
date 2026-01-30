package org.stella.typecheck.exceptions;

public class TypeCheckException extends RuntimeException {
  public TypeCheckException(String message, ErrorType errorType) {
    super(message);
    this.errorType = errorType;
  }

  public TypeCheckException(ErrorType errorType) {
    this.errorType = errorType;
  }

  private final ErrorType errorType;

  @Override
  public String getMessage() {
    return "Error: " + errorType + "\n";
  }

  public enum ErrorType {

    ERROR_UNEXPECTED_TYPE_FOR_PARAMETER,


    ERROR_UNEXPECTED_TYPE_FOR_EXPRESSION,


    ERROR_UNEXPECTED_LAMBDA,


    ERROR_NOT_A_FUNCTION,


    ERROR_UNDEFINED_VARIABLE,


    ERROR_MISSING_MAIN,


    ERROR_UNEXPECTED_TUPLE,


    ERROR_NOT_A_TUPLE;

  }

}
