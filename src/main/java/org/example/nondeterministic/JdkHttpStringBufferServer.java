/*
 * (c) Copyright 2025 Ryan Yeats. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.example.nondeterministic;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;

public class JdkHttpStringBufferServer implements Closeable {

  private final HttpServer server;
  private static final StringBuffer STRING_BUFFER = new StringBuffer();

  public JdkHttpStringBufferServer(Executor executor) throws IOException {
    server = HttpServer.create(new InetSocketAddress(8080), 0);
    server.setExecutor(executor);
    server.createContext("/").setHandler(new StringBufferHandler());
    server.start();
    System.out.println("ready");
  }

  @Override
  public void close() throws IOException {
    server.stop(0);
  }

  static class StringBufferHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
      String method = exchange.getRequestMethod();
      String path = exchange.getRequestURI().getPath().replaceFirst("/", "");

      switch (method) {
        case "PUT":
        case "POST":
          //          appendToBuffer(STRING_BUFFER,path);
          appendToBuffer(STRING_BUFFER, new String(exchange.getRequestBody().readAllBytes()));
          break;
        case "GET":
          readBuffer(exchange);
          break;
        default:
          sendResponse(exchange, 405, "Method Not Allowed");
      }
    }

    private void readBuffer(HttpExchange exchange) throws IOException {
      System.out.println("Read with contents: " + STRING_BUFFER);
      exchange.getResponseHeaders().add("Content-Type", "text/plain");
      sendResponse(exchange, 200, STRING_BUFFER.toString());
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String message)
        throws IOException {
      sendResponse(exchange, statusCode, message.getBytes(StandardCharsets.UTF_8));
    }

    private void sendResponse(HttpExchange exchange, int statusCode, byte[] responseBytes)
        throws IOException {
      exchange.sendResponseHeaders(statusCode, responseBytes.length);
      try (OutputStream os = exchange.getResponseBody()) {
        os.write(responseBytes);
      }
    }
  }

  public static synchronized void testSync(StringBuffer buffer) {
    buffer.append(",");
  }

  public static void appendToBuffer(StringBuffer buffer, String str) {
    buffer.append(str);
    testSync(buffer);
    Thread.yield();
    buffer.append(str);
    try {
      Thread.sleep(10);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    buffer.append(str);
  }
}
