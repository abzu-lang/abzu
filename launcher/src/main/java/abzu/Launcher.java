package abzu;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

public final class Launcher {
  private Launcher() {}

  public static void main(String[] args) throws IOException {
    Source source;
    Map<String, String> options = new HashMap<>();
    String file = null;
    for (String arg : args) {
      if (!parseOption(options, arg) && file == null) {
        file = arg;
      }
    }

    if (file == null) {
      // @formatter:off
      source = Source.newBuilder("abzu", new InputStreamReader(System.in), "<stdin>").build();
      // @formatter:on
    } else {
      source = Source.newBuilder("abzu", new File(file)).build();
    }

    System.exit(executeSource(source, options));
  }

  private static int executeSource(Source source, Map<String, String> options) {
    Context context;
    try {
      context = Context.newBuilder("abzu").in(System.in).out(System.out).options(options).build();
    } catch (IllegalArgumentException e) {
      System.err.println(e.getMessage());
      return 1;
    }
    System.out.println("== running on " + context.getEngine());

    try {
      Value result = context.eval(source);
      if (context.getBindings("abzu").getMember("main") == null) {
        System.err.println("No function main() defined in Abzu source file.");
        return 1;
      }
      if (!result.isNull()) {
        System.out.println(result.toString());
      }
      return 0;
    } catch (PolyglotException ex) {
      if (ex.isInternalError()) {
        // for internal errors we print the full stack trace
        ex.printStackTrace();
      } else {
        System.err.println(ex.getMessage());
      }
      return 1;
    } finally {
      context.close();
    }
  }

  private static boolean parseOption(Map<String, String> options, String arg) {
    if (arg.length() <= 2 || !arg.startsWith("--")) {
      return false;
    }
    int eqIdx = arg.indexOf('=');
    String key;
    String value;
    if (eqIdx < 0) {
      key = arg.substring(2);
      value = null;
    } else {
      key = arg.substring(2, eqIdx);
      value = arg.substring(eqIdx + 1);
    }

    if (value == null) {
      value = "true";
    }
    int index = key.indexOf('.');
    String group = key;
    if (index >= 0) {
      group = group.substring(0, index);
    }
    options.put(key, value);
    return true;
  }

}