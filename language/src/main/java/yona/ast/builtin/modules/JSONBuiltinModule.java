package yona.ast.builtin.modules;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.antlr.v4.runtime.*;
import yona.YonaException;
import yona.ast.builtin.BuiltinNode;
import yona.json.JSONLexer;
import yona.json.JSONParser;
import yona.json.JSONParserVisitor;
import yona.runtime.Seq;
import yona.runtime.async.Promise;
import yona.runtime.exceptions.BadArgException;
import yona.runtime.stdlib.Builtins;
import yona.runtime.stdlib.ExportedFunction;

import java.nio.ByteBuffer;

@BuiltinModuleInfo(moduleName = "JSON")
public final class JSONBuiltinModule implements BuiltinModule {
  @NodeInfo(shortName = "parse")
  abstract static class ParseBuiltin extends BuiltinNode {
    @Specialization
    @CompilerDirectives.TruffleBoundary
    public Object parse(Seq str) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      JSONLexer lexer = new JSONLexer(seqToCharStream(str));
      TokenStream tokens = new CommonTokenStream(lexer);
      JSONParser parser = new JSONParser(tokens);
      JSONParserVisitor visitor = new JSONParserVisitor();

      return visitor.visit(parser.json());
    }

    @Specialization
    @CompilerDirectives.TruffleBoundary
    public Object parse(Promise promise) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      return promise.map(obj -> {
        if (obj instanceof Seq) {
          CompilerDirectives.transferToInterpreterAndInvalidate();
          Seq str = (Seq) obj;
          JSONLexer lexer = new JSONLexer(seqToCharStream(str));
          TokenStream tokens = new CommonTokenStream(lexer);
          JSONParser parser = new JSONParser(tokens);
          JSONParserVisitor visitor = new JSONParserVisitor();

          return visitor.visit(parser.json());
        } else {
          return YonaException.typeError(this, obj);
        }
      }, this);
    }

    private CharStream seqToCharStream(Seq str) {
      CharStream charStream;
      if (str.isString()) {
        charStream = CharStreams.fromString(str.asJavaString(this));
      } else {
        long len = str.length();

        if (len > Integer.MAX_VALUE) {
          throw new BadArgException("Sequence too long to be converted to Java byte array", this);
        }

        ByteBuffer byteBuffer = ByteBuffer.allocate((int) len);
        if (str.asBytes(byteBuffer)) {
          byteBuffer.limit(byteBuffer.position());
          byteBuffer.position(0);
          charStream = CodePointCharStream.fromBuffer(CodePointBuffer.withBytes(byteBuffer));
        } else {
          throw new BadArgException("Provided string is not a valid byte sequence or Yona string: " + str, this);
        }
      }
      return charStream;
    }
  }

  public Builtins builtins() {
    return new Builtins(
        new ExportedFunction(JSONBuiltinModuleFactory.ParseBuiltinFactory.getInstance())
    );
  }
}
