package io.zsxh.github.blocking;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * $ netcat localhost 3000
 *
 * Blocking APIs waste resources, increase costs
 */
public class SynchronousEcho {

  public static void main(String[] args) throws IOException {
    ServerSocket server = new ServerSocket();
    server.bind(new InetSocketAddress(3000));
    while (true) {
      Socket socket = server.accept();
      new Thread(clientHandler(socket)).start();
    }
  }

  private static Runnable clientHandler(Socket socket) {
    return () -> {
      try {
        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
        String line = "";
        while (!"/quit".equals(line)) {
          line = reader.readLine();
          System.out.println("~ " + line);
          writer.write(line + "\n");
          writer.flush();
        }
        socket.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
    };
  }
}
