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
package org.example.net;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.Executor;

public class JdkHttpServer implements Closeable {

  private final HttpServer server;
//  private static FileSystem fs = Jimfs.newFileSystem(Configuration.unix());
  private static FileSystem fs = FileSystems.getDefault();

  public JdkHttpServer(Executor executor) throws IOException {
    server = HttpServer.create(new InetSocketAddress(8080), 0);
    server.setExecutor(executor);
    server.createContext("/").setHandler(new FileHandler());
    server.start();
    System.out.println("ready");
  }

  @Override
  public void close() throws IOException {
    server.stop(0);
  }

  static class FileHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
      String method = exchange.getRequestMethod();
      String path = exchange.getRequestURI().getPath().replaceFirst("/", "");

      if (path.isEmpty() || path.equals("/")) {
        sendResponse(exchange, 400, "File path missing");
        return;
      }

      Path filePath = fs.getPath(path);

      switch (method) {
        case "POST":
          createFile(exchange, filePath);
          break;
        case "PUT":
          updateFile(exchange, filePath);
          break;
        case "GET":
          readFile(exchange, filePath);
          break;
        default:
          sendResponse(exchange, 405, "Method Not Allowed");
      }
    }

    private void createFile(HttpExchange exchange, Path filePath) throws IOException {

      filePath = Paths.get("").toAbsolutePath().resolve("target").resolve(filePath);
      byte[] body = exchange.getRequestBody().readAllBytes();
      System.out.println("Creating "+ filePath+" with contents: "+new String(body));
      Files.createDirectories(filePath.getParent());
      Files.write(filePath, body, StandardOpenOption.TRUNCATE_EXISTING);
      sendResponse(exchange, 201, "File created");
    }

    private void updateFile(HttpExchange exchange, Path filePath) throws IOException {
      filePath = Paths.get("").toAbsolutePath().resolve("target").resolve(filePath);
      if (!Files.exists(filePath)) {
        sendResponse(exchange, 404, "File not found");
        return;
      }
      byte[] body = exchange.getRequestBody().readAllBytes();
      System.out.println("Updating "+ filePath+" with contents: "+new String(body));
      Files.write(filePath, body, StandardOpenOption.APPEND);
      sendResponse(exchange, 200, "File updated");
    }

    private void readFile(HttpExchange exchange, Path filePath) throws IOException {
      filePath = Paths.get("").toAbsolutePath().resolve("target").resolve(filePath);
      if (!Files.exists(filePath)) {
        sendResponse(exchange, 404, "File not found");
        return;
      }
      String content = Files.readString(filePath);
      System.out.println("Read "+ filePath+" with contents: "+content);
      exchange.getResponseHeaders().add("Content-Type", "text/plain");
      sendResponse(exchange, 200, content);
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
}
