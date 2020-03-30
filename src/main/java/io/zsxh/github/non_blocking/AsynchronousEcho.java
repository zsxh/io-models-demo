package io.zsxh.github.non_blocking;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Iterator;
import java.util.regex.Pattern;

/**
 * Using non-blocking I/O is doable yet it significantly increase the code complexity
 * compared to the intitial version that was using blocking APIs.
 *
 * The echo protocol needs 2 states for reading and writing back data: reading, or finishing writing.
 * For more elaborate TCP protocols you can easily anticipate the need for more complicated state machines.
 */
public class AsynchronousEcho {

  public static void main(String[] args) throws IOException {
    Selector selector = Selector.open();

    ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
    serverSocketChannel.bind(new InetSocketAddress(3000));
    serverSocketChannel.configureBlocking(false); // We need to put the channel to non-blocking mode
    // The selector will notify of incoming connections.
    serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

    while (true) {
      selector.select();
      Iterator<SelectionKey> it = selector.selectedKeys().iterator();
      while (it.hasNext()) {
        SelectionKey key = it.next();
        if (key.isAcceptable()) {
          newConnection(selector, key);
        } else if (key.isReadable()) {
          echo(key);
        } else if (key.isWritable()) {
          continueEcho(selector, key);
        }
        it.remove();
      }
    }
  }

  /**
   * The Context class keeps state related to the handling of a TCP connection.
   */
  private static class Context {
    private final ByteBuffer niobuffer = ByteBuffer.allocate(512);
    private String currentLine = "";
    private boolean terminating = false;
  }

  private static final HashMap<SocketChannel, Context> contexts = new HashMap<>();

  private static void newConnection(Selector selector, SelectionKey key) throws IOException {
    ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
    SocketChannel socketChannel = serverSocketChannel.accept();
    // We set the channel to non-blocking, and declare interest in read operations.
    socketChannel.configureBlocking(false).register(selector, SelectionKey.OP_READ);
    contexts.put(socketChannel, new Context()); // We keep all connection states in a hash map.
  }

  private static final Pattern QUIT = Pattern.compile("(\\r)?(\\n)?/quit$");

  private static void echo(SelectionKey key) throws IOException {
    SocketChannel socketChannel = (SocketChannel) key.channel();
    Context context = contexts.get(socketChannel);
    try {
      socketChannel.read(context.niobuffer);
      context.niobuffer.flip();
      context.currentLine =
          context.currentLine + Charset.defaultCharset().decode(context.niobuffer);
      if (QUIT.matcher(context.currentLine).find()) {
        // if we find a line ending with /quit, then we are terminating the connection.
        context.terminating = true;
      } else if (context.currentLine.length() > 16) {
        context.currentLine = context.currentLine.substring(8);
      }
      // Java NIO buffers need positional manipulations: the buffer has read data, so to
      // write it back to the client we need to flip and return to the start position.
      context.niobuffer.flip();
      int count = socketChannel.write(context.niobuffer);
      if (count < context.niobuffer.limit()) {
        key.cancel();
        socketChannel.register(key.selector(), SelectionKey.OP_WRITE);
      } else {
        context.niobuffer.clear();
        if (context.terminating) {
          cleanup(socketChannel);
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
      cleanup(socketChannel);
    }

  }

  private static void cleanup(SocketChannel socketChannel) throws IOException {
    socketChannel.close();
    contexts.remove(socketChannel);
  }

  private static void continueEcho(Selector selector, SelectionKey key) throws IOException {
    SocketChannel socketChannel = (SocketChannel) key.channel();
    Context context = contexts.get(socketChannel);
    try {
      int remainingBytes = context.niobuffer.limit() - context.niobuffer.position();
      int count = socketChannel.write(context.niobuffer);
      if (count == remainingBytes) {
        context.niobuffer.clear();
        key.cancel();
        if (context.terminating) {
          cleanup(socketChannel);
        } else {
          socketChannel.register(selector, SelectionKey.OP_READ);
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
      cleanup(socketChannel);
    }
  }
}
