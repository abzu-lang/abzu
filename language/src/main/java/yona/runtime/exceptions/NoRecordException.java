package yona.runtime.exceptions;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.nodes.Node;
import yona.YonaException;
import yona.runtime.annotations.ExceptionSymbol;

@ExceptionSymbol("norecord")
public final class NoRecordException extends YonaException {
  @CompilerDirectives.TruffleBoundary
  public NoRecordException(String recordType, Node location) {
    super("NoRecordException: " + recordType, location);
  }
}
