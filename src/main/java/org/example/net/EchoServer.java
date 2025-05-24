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

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EchoServer implements Closeable {

  private volatile boolean running = true;
  private ServerSocket serverSocket;

  public EchoServer() {}

  public EchoServer(ExecutorService executorService, int port) {
    executorService.submit(() -> listen(port));
  }

  public void listen(int port) {
    try (ServerSocket serverSocket = new ServerSocket(port)) {
      System.out.println("Server is listening on port " + port);
      this.serverSocket = serverSocket;
      while (running) {
        Socket clientSocket = serverSocket.accept();
        //                System.out.println("Client connected: " + clientSocket.getInetAddress());

        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true)) {
          String inputLine;
          while ((inputLine = reader.readLine()) != null) {
            //                        System.out.println("Received from client: " + inputLine);
            //              writer.println("Server echo: " + inputLine);
            writer.println(inputLine.toLowerCase(Locale.ROOT));
          }
          //                    System.out.println("Client disconnected: " +
          // clientSocket.getInetAddress());

        } catch (IOException e) {
          System.err.println("Error handling client: " + e.getMessage());
        }
        try {
          Thread.sleep(1);
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      }
    } catch (IOException e) {
      //            System.err.println("Error starting server: " + e.getMessage());
    }
  }

  @Override
  public void close() {
    running = false;
    if (serverSocket != null) {
      try {
        serverSocket.close();
      } catch (IOException e) {

      }
    }
  }

  public static void main(String... args) throws Exception {
    try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
      EchoServer server = new EchoServer(executor, 4242);
      EchoClient client = new EchoClient("localhost", 4242);
      System.out.println(client.send("TEST"));
      server.close();
    }
  }
}
