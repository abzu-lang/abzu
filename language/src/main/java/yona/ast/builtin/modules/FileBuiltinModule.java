package yona.ast.builtin.modules;


import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import yona.TypesGen;
import yona.YonaException;
import yona.YonaLanguage;
import yona.ast.builtin.BuiltinNode;
import yona.runtime.*;
import yona.runtime.async.Promise;
import yona.runtime.exceptions.BadArgException;
import yona.runtime.stdlib.Builtins;
import yona.runtime.stdlib.ExportedFunction;

import java.io.File;
import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;

// TODO use FlagsUtils
@BuiltinModuleInfo(moduleName = "File")
public final class FileBuiltinModule implements BuiltinModule {
  protected static final int FILE_READ_BUFFER_SIZE = 4096;

  protected static final class FileTuple extends Tuple {
    public FileTuple(AsynchronousFileChannel fileHandle, Object readBuffer, long position, Seq additionalOptions, Seq path) {
      this.items = new Object[]{
          new NativeObject<>(fileHandle), readBuffer, position, additionalOptions, path
      };
    }

    public FileTuple seek(long position) {
      return new FileTuple(fileHandle(), readBuffer(), position, additionalOptions(), path());
    }

    public AsynchronousFileChannel fileHandle() {
      return ((NativeObject<AsynchronousFileChannel>) items[0]).getValue();
    }

    public Object readBuffer() {
      return items[1];
    }

    public long position() {
      return (long) items[2];
    }

    public Seq additionalOptions() {
      return (Seq) items[3];
    }

    public Seq path() {
      return (Seq) items[4];
    }
  }

  protected static final class FileContextManager extends ContextManager<FileTuple> {
    private final Context context;

    public FileContextManager(FileTuple fileTuple, Context context) {
      super("file", context.lookupGlobalFunction("File", "run"), fileTuple);
      this.context = context;
    }

    public FileContextManager copy(Object readBuffer, long position, Node node) {
      final FileTuple fileTuple = getData(FileTuple.class, node);
      return new FileContextManager(new FileTuple(fileTuple.fileHandle(), readBuffer, position, fileTuple.additionalOptions(), fileTuple.path()), context);
    }

    public static FileContextManager adapt(ContextManager<?> contextManager, Context context, Node node) {
      return new FileContextManager(contextManager.getData(FileTuple.class, node), context);
    }
  }

  protected static final class FileOptions {
    final Set<OpenOption> openOptions = new HashSet<>();
    Seq additionalOptions = Seq.EMPTY;

    public void addAdditionalOption(Object option) {
      this.additionalOptions = this.additionalOptions.insertFirst(option);
    }
  }

  private abstract static class NewFileNode extends BuiltinNode {
    protected Object openFile(Path path, FileOptions fileOptions, Context context) {
      try {
        final AsynchronousFileChannel asynchronousFileChannel = AsynchronousFileChannel.open(path, fileOptions.openOptions, context.ioExecutor);
        return new FileContextManager(new FileTuple(asynchronousFileChannel, Unit.INSTANCE, 0L, fileOptions.additionalOptions, Seq.fromCharSequence(path.toString())), context);
      } catch (IOException e) {
        throw new yona.runtime.exceptions.IOException(e, this);
      }
    }

    protected Object openOptions(Object modes) {
      if (modes instanceof Object[]) {
        return openOptionsFromArray((Object[]) modes);
      } else if (modes instanceof yona.runtime.Set) {
        return openOptionsFromArray(((yona.runtime.Set) modes).toArray());
      } else {
        return ((Promise) modes).map(this::openOptions, this);
      }
    }

    private FileOptions openOptionsFromArray(Object[] modes) {
      final FileOptions fileOptions = new FileOptions();
      for (Object el : modes) {
        try {
          Symbol symbol = TypesGen.expectSymbol(el);
          if (isModeAdditionalOption(symbol)) {
            fileOptions.addAdditionalOption(symbol);
          } else {
            fileOptions.openOptions.add(modeSymbolToOpenOption(symbol));
          }
        } catch (UnexpectedResultException e) {
          throw new BadArgException(e, this);
        }
      }
      return fileOptions;
    }

    private boolean isModeAdditionalOption(Symbol symbol) {
      final String symbolName = symbol.asString();
      return switch (symbolName) {
        case "binary" -> true;
        case "delete_on_close" -> true;
        default -> false;
      };
    }

    private OpenOption modeSymbolToOpenOption(Symbol symbol) {
      final String symbolName = symbol.asString();
      return switch (symbolName) {
        case "read" -> StandardOpenOption.READ;
        case "write" -> StandardOpenOption.WRITE;
        case "append" -> StandardOpenOption.APPEND;
        case "truncate_existing" -> StandardOpenOption.TRUNCATE_EXISTING;
        case "create" -> StandardOpenOption.CREATE;
        case "create_new" -> StandardOpenOption.CREATE_NEW;
        case "sparse" -> StandardOpenOption.SPARSE;
        case "sync" -> StandardOpenOption.SYNC;
        case "dsync" -> StandardOpenOption.DSYNC;
        default -> throw new BadArgException("Unknown file mode: " + symbol, this);
      };
    }

    protected Object openPath(Path path, yona.runtime.Set modes, Context context) {
      final Object fileOptionsObj = openOptions(modes.unwrapPromises(this));
      if (fileOptionsObj instanceof FileOptions) {
        return openFile(path, (FileOptions) fileOptionsObj, context);
      } else { // Promise
        return ((Promise) fileOptionsObj).map(fileOptions -> openFile(path, (FileOptions) fileOptions, context), this);
      }
    }
  }

  @NodeInfo(shortName = "run")
  abstract static class RunBuiltin extends BuiltinNode {
    @Specialization
    @CompilerDirectives.TruffleBoundary
    public Object run(ContextManager<?> contextManager, Function function, @CachedLibrary(limit = "3") InteropLibrary dispatch, @CachedContext(YonaLanguage.class) Context context) {
      final FileContextManager fileContextManager = FileContextManager.adapt(contextManager, context, this);
      boolean shouldClose = true;
      try {
        final Object result = dispatch.execute(function);
        if (result instanceof Promise resultPromise) {
          shouldClose = false;
          return resultPromise.map(value -> {
            try {
              return value;
            } finally {
              RunBuiltin.closeFile(fileContextManager, value, this, context);
            }
          }, exception -> RunBuiltin.closeFile(fileContextManager, exception, this, context), this);
        } else {
          return result;
        }
      } catch (UnsupportedTypeException | UnsupportedMessageException | ArityException e) {
        throw new YonaException(e, this);
      } finally {
        if (shouldClose) {
          RunBuiltin.closeFile(fileContextManager, Unit.INSTANCE, this, context);
        }
      }
    }

    private static <T> T closeFile(FileContextManager fileContextManager, T result, Node node, Context context) {
      try {
        final FileTuple fileTuple = fileContextManager.getData(FileTuple.class, node);
        final AsynchronousFileChannel fileHandle = fileTuple.fileHandle();
        fileHandle.close();
        if (fileTuple.additionalOptions().contains(context.symbol("delete_on_close"), node)) {
          new File(fileTuple.path().asJavaString(node)).delete();
        }
        return result;
      } catch (IOException e) {
        throw new yona.runtime.exceptions.IOException(e, node);
      }
    }
  }

  @NodeInfo(shortName = "open")
  abstract static class FileOpenNode extends NewFileNode {
    @Specialization
    @CompilerDirectives.TruffleBoundary
    @SuppressWarnings("unchecked")
    public Object open(Seq uri, yona.runtime.Set modes, @CachedContext(YonaLanguage.class) Context context) {
      final Path path = Paths.get(uri.asJavaString(this).replaceFirst("^~", Matcher.quoteReplacement(System.getProperty("user.home"))));
      return openPath(path, modes, context);
    }
  }

  @NodeInfo(shortName = "path")
  abstract static class FilePathNode extends BuiltinNode {
    @Specialization
    @CompilerDirectives.TruffleBoundary
    public Seq path(ContextManager<?> contextManager, @CachedContext(YonaLanguage.class) Context context) {
      final FileContextManager fileContextManager = FileContextManager.adapt(contextManager, context, this);
      return fileContextManager.getData(FileTuple.class, this).path();
    }
  }

  @NodeInfo(shortName = "delete")
  abstract static class FileDeleteNode extends BuiltinNode {
    @Specialization
    @CompilerDirectives.TruffleBoundary
    public Unit path(ContextManager<?> contextManager, @CachedContext(YonaLanguage.class) Context context) {
      final FileContextManager fileContextManager = FileContextManager.adapt(contextManager, context, this);
      try {
        Files.deleteIfExists(Paths.get(fileContextManager.getData(FileTuple.class, this).path().asJavaString(this)));
        return Unit.INSTANCE;
      } catch (IOException e) {
        throw new yona.runtime.exceptions.IOException(e, this);
      }
    }
  }

  @NodeInfo(shortName = "seek")
  abstract static class FileSeekNode extends NewFileNode {
    @Specialization
    @CompilerDirectives.TruffleBoundary
    @SuppressWarnings("unchecked")
    public FileTuple open(ContextManager<?> contextManager, long position, @CachedContext(YonaLanguage.class) Context context) {
      final FileContextManager fileContextManager = FileContextManager.adapt(contextManager, context, this);
      return fileContextManager.getData(FileTuple.class, this).seek(position);
    }
  }

  @NodeInfo(shortName = "make_temp")
  abstract static class CreateTempFileNode extends NewFileNode {
    @Specialization
    @CompilerDirectives.TruffleBoundary
    @SuppressWarnings("unchecked")
    public Object open(Seq prefix, Seq suffix, yona.runtime.Set modes, @CachedContext(YonaLanguage.class) Context context) {
      try {
        final File tempFile = File.createTempFile(prefix.asJavaString(this), suffix.asJavaString(this));
        return openPath(tempFile.toPath(), modes, context);
      } catch (IOException e) {
        throw new yona.runtime.exceptions.IOException(e, this);
      }
    }
  }

  @NodeInfo(shortName = "list_dir")
  abstract static class FileListNode extends BuiltinNode {
    @Specialization
    @CompilerDirectives.TruffleBoundary
    public Seq path(Seq path, @CachedContext(YonaLanguage.class) Context context) {
      try {
        final Collection<TruffleFile> files = context.getEnv().getPublicTruffleFile(path.asJavaString(this)).list();
        Seq ret = Seq.EMPTY;
        for (TruffleFile file : files) {
          ret = ret.insertLast(Seq.fromCharSequence(file.getPath()));
        }
        return ret;
      } catch (IOException e) {
        throw new yona.runtime.exceptions.IOException(e, this);
      }
    }
  }

  @NodeInfo(shortName = "read_line")
  abstract static class FileReadLineNode extends BuiltinNode {
    @Specialization
    @CompilerDirectives.TruffleBoundary
    public Promise readline(ContextManager<?> contextManager, @CachedContext(YonaLanguage.class) Context context, @CachedLibrary(limit = "3") InteropLibrary interopLibrary) {
      final FileContextManager fileContextManager = FileContextManager.adapt(contextManager, context, this);
      final FileTuple fileTuple = fileContextManager.getData(FileTuple.class, this);
      final AsynchronousFileChannel asynchronousFileChannel = ((NativeObject<AsynchronousFileChannel>) fileTuple.get(0)).getValue();
      final ByteBuffer buffer = ByteBuffer.allocate(FILE_READ_BUFFER_SIZE);
      final Node thisNode = this;
      final Promise promise = new Promise(interopLibrary);

      try {
        asynchronousFileChannel.read(buffer, fileTuple.position(), buffer, new CompletionHandler<>() {
          @Override
          public void completed(Integer result, ByteBuffer attachment) {
            boolean fulfilled = false;
            if (result <= 0) {
              promise.fulfil(context.symbol("eof"), thisNode);
              return;
            }


            attachment.flip();
            int length = attachment.limit();

            ByteBuffer output;

            if (fileTuple.readBuffer() instanceof ByteBuffer) {
              output = (ByteBuffer) fileTuple.readBuffer();
              output.flip();
            } else {
              output = ByteBuffer.allocate(length);
            }

            for (int i = 0; i < length; i++) {
              byte b = attachment.get();
              if (b == '\r') {
                continue;
              }
              if (b == '\n') {
                Seq resultSeq = bytesToSeq(output, fileTuple.additionalOptions(), context, thisNode);
                promise.fulfil(new Tuple(context.symbol("ok"), resultSeq, fileContextManager.copy(null, fileTuple.position() + i + 1, thisNode)), thisNode);
                fulfilled = true;
                break;
              } else {
                output.put(b);
              }
            }
            attachment.clear();

            if (!fulfilled) {
              readline(fileContextManager.copy(output, fileTuple.position() + length, thisNode), context, interopLibrary).map(res -> {
                promise.fulfil(res, thisNode);
                return res;
              }, thisNode);
            }
          }

          @Override
          public void failed(Throwable exc, ByteBuffer attachment) {
            promise.fulfil(new YonaException(exc.getMessage(), thisNode), thisNode);
          }
        });
      } catch (Exception ex) {
        promise.fulfil(new yona.runtime.exceptions.IOException(ex.getMessage(), thisNode), thisNode);
      }

      return promise;
    }

    private static Seq bytesToSeq(ByteBuffer byteBuffer, Seq additionalFileOptions, Context context, Node caller) {
      byteBuffer.flip();
      if (additionalFileOptions.contains(context.symbol("binary"), caller)) {
        return Seq.fromByteBuffer(byteBuffer);
      } else {
        return Seq.fromCharSequence(StandardCharsets.UTF_8.decode(byteBuffer));
      }
    }
  }

  @NodeInfo(shortName = "read")
  abstract static class FileReadFileNode extends BuiltinNode {
    @Specialization
    @CompilerDirectives.TruffleBoundary
    public Promise readfile(ContextManager<?> contextManager, @CachedContext(YonaLanguage.class) Context context, @CachedLibrary(limit = "3") InteropLibrary interopLibrary) {
      final FileContextManager fileContextManager = FileContextManager.adapt(contextManager, context, this);
      final FileTuple fileTuple = fileContextManager.getData(FileTuple.class, this);
      final class CatenateCompletionHandler implements CompletionHandler<Integer, ByteBuffer> {
        final AsynchronousFileChannel channel;
        final Promise promise;
        long position;
        Seq seq = Seq.EMPTY;

        CatenateCompletionHandler(AsynchronousFileChannel channel, Promise promise, long position) {
          this.channel = channel;
          this.promise = promise;
          this.position = position;
        }

        @Override
        public void completed(Integer result, ByteBuffer attachment) {
          if (result <= 0) {
            promise.fulfil(seq, FileReadFileNode.this);
          } else {
            attachment.flip();

            if (fileTuple.additionalOptions().contains(context.symbol("binary"), FileReadFileNode.this)) {
              seq = Seq.catenate(seq, Seq.fromByteBuffer(attachment));
            } else {
              seq = Seq.catenate(seq, Seq.fromCharSequence(StandardCharsets.UTF_8.decode(attachment)));
            }
            attachment.clear();

            position += result;
            channel.read(attachment, position, attachment, this);
          }
        }

        @Override
        public void failed(Throwable exc, ByteBuffer attachment) {
          promise.fulfil(new yona.runtime.exceptions.IOException(exc.getMessage(), FileReadFileNode.this), FileReadFileNode.this);
        }
      }

      final ByteBuffer buffer = ByteBuffer.allocate(FILE_READ_BUFFER_SIZE);
      final Promise promise = new Promise(interopLibrary);

      try {
        fileTuple.fileHandle().read(buffer, fileTuple.position(), buffer, new CatenateCompletionHandler(fileTuple.fileHandle(), promise, fileTuple.position()));
      } catch (Exception ex) {
        promise.fulfil(new YonaException(ex.getMessage(), this), this);
      }

      return promise;
    }
  }

  @NodeInfo(shortName = "write")
  abstract static class FileWriteFileNode extends BuiltinNode {
    @Specialization
    @CompilerDirectives.TruffleBoundary
    public Promise writefile(ContextManager<?> contextManager, Seq data, @CachedLibrary(limit = "3") InteropLibrary interopLibrary, @CachedContext(YonaLanguage.class) Context context) {
      final FileContextManager fileContextManager = FileContextManager.adapt(contextManager, context, this);
      final FileTuple fileTuple = fileContextManager.getData(FileTuple.class, this);
      final Node thisNode = this;
      final class WriteCompletionHandler implements CompletionHandler<Integer, Promise> {

        @Override
        public void completed(Integer result, Promise attachment) {
          attachment.fulfil(fileContextManager.copy(Unit.INSTANCE, fileTuple.position() + result, thisNode), thisNode);
        }

        @Override
        public void failed(Throwable exc, Promise attachment) {
          attachment.fulfil(new yona.runtime.exceptions.IOException(exc.getMessage(), FileWriteFileNode.this), FileWriteFileNode.this);
        }
      }

      final Promise promise = new Promise(interopLibrary);

      try {
        final ByteBuffer byteBuffer = data.asByteBuffer(this);
        fileTuple.fileHandle().write(byteBuffer, fileTuple.position(), promise, new WriteCompletionHandler());
      } catch (BufferOverflowException ex) {
        promise.fulfil(new yona.runtime.exceptions.IOException(ex, this), this);
      } catch (Exception ex) {
        promise.fulfil(new YonaException(ex, this), this);
      }

      return promise;
    }
  }

  public Builtins builtins() {
    return new Builtins(
        new ExportedFunction(FileBuiltinModuleFactory.FileOpenNodeFactory.getInstance()),
        new ExportedFunction(FileBuiltinModuleFactory.CreateTempFileNodeFactory.getInstance()),
        new ExportedFunction(FileBuiltinModuleFactory.FileDeleteNodeFactory.getInstance()),
        new ExportedFunction(FileBuiltinModuleFactory.FilePathNodeFactory.getInstance()),
        new ExportedFunction(FileBuiltinModuleFactory.FileSeekNodeFactory.getInstance()),
        new ExportedFunction(FileBuiltinModuleFactory.FileListNodeFactory.getInstance()),
        new ExportedFunction(FileBuiltinModuleFactory.FileReadLineNodeFactory.getInstance()),
        new ExportedFunction(FileBuiltinModuleFactory.FileReadFileNodeFactory.getInstance()),
        new ExportedFunction(FileBuiltinModuleFactory.FileWriteFileNodeFactory.getInstance()),
        new ExportedFunction(FileBuiltinModuleFactory.RunBuiltinFactory.getInstance())
    );
  }
}
