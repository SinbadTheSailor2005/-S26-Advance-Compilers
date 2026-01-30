package org.stella.typecheck.exceptions;

import org.syntax.stella.Absyn.Type;

public class TypeCheckException extends RuntimeException {
  public TypeCheckException(
          String message, Type expectedType, Type actualType,
          ErrorType errorType) {
    super(message);
    this.expectedType = expectedType;
    this.actualType = actualType;
    this.errorType = errorType;
  }
  String contextMessage = "";
  private Type expectedType;
  private Type actualType;

  public TypeCheckException(
          ErrorType errorType, String message) {
    this.errorType = errorType;
    this.contextMessage = message;
  }

  public TypeCheckException(
          ErrorType errorType, Type expectedType, Type actualType, ) {
    this.expectedType = expectedType;
    this.actualType = actualType;
    this.errorType = errorType;
  }

  private final ErrorType errorType;

  @Override
  public String getMessage() {
    return "Error: " + errorType + "\n" + getErrorContext();
  }


  private String getErrorContext() {
    if (expectedType == null || actualType == null) {
      return contextMessage;
    }
    return "Expected type: " + expectedType + ", but got: " + actualType + "\n";
  }

  public enum ErrorType {

    ERROR_MISSING_MAIN,


    ERROR_INCORRECT_TYPE_OF_MAIN,


    ERROR_UNDEFINED_VARIABLE,


    ERROR_ILLEGAL_NEGATIVE_LITERAL,


    ERROR_UNEXPECTED_TYPE_FOR_EXPRESSION,


    ERROR_NOT_A_FUNCTION,


    ERROR_NOT_A_TUPLE,


    ERROR_NOT_A_RECORD,


    ERROR_NOT_A_LIST,


    ERROR_UNEXPECTED_LAMBDA,


    ERROR_UNEXPECTED_TYPE_FOR_PARAMETER,


    ERROR_UNEXPECTED_TUPLE,


    ERROR_UNEXPECTED_RECORD,


    ERROR_UNEXPECTED_VARIANT,


    ERROR_UNEXPECTED_LIST,


    ERROR_UNEXPECTED_INJECTION,


    ERROR_MISSING_RECORD_FIELDS,


    ERROR_UNEXPECTED_RECORD_FIELDS,


    ERROR_UNEXPECTED_FIELD_ACCESS,


    ERROR_UNEXPECTED_VARIANT_LABEL,


    ERROR_MISSING_VARIANT_LABELS,


    ERROR_TUPLE_INDEX_OUT_OF_BOUNDS,


    ERROR_UNEXPECTED_TUPLE_LENGTH,


    ERROR_AMBIGUOUS_SUM_TYPE,


    ERROR_AMBIGUOUS_VARIANT_TYPE,


    ERROR_AMBIGUOUS_LIST_TYPE,


    ERROR_ILLEGAL_EMPTY_MATCHING,


    ERROR_NONEXHAUSTIVE_MATCH_PATTERNS,


    ERROR_UNEXPECTED_PATTERN_FOR_TYPE,


    ERROR_DUPLICATE_RECORD_FIELDS,


    ERROR_DUPLICATE_RECORD_TYPE_FIELDS,


    ERROR_DUPLICATE_VARIANT_TYPE_FIELDS;


    /* * OPTIONAL EXTENSIONS (Дополнительные коды для бонусных заданий):
     * Если будешь делать бонусы (Nullary functions, Multi-param), раскомментируй это:
     */


  }

}
