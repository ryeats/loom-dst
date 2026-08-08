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
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class InMemoryNetworkServer {
  private final int port;
  private volatile boolean running = true;

  public InMemoryNetworkServer(int port) {
    this.port = port;
  }

  public void start() {
    // Use a Virtual Thread to host the main accept loop itself
    Thread.ofVirtual()
        .name("Server-Loop-" + port)
        .start(
            () -> {
              try (ServerSocket serverSocket = new InMemoryServerSocket(port)) {
                System.out.println("In-memory server listening on port " + port);

                while (running) {
                  Socket clientSocket = serverSocket.accept();

                  // Instantly spin up a unique virtual thread for each client connection
                  Thread.ofVirtual()
                      .name("Client-Handler-" + clientSocket.getRemoteSocketAddress())
                      .start(() -> handleClient(clientSocket));
                }
              } catch (IOException e) {
                if (running) e.printStackTrace();
              }
            });
  }

  private void handleClient(Socket socket) {
    try (socket;
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

      String line;
      while ((line = in.readLine()) != null) {
        if ("QUIT".equalsIgnoreCase(line)) break;
        out.println("Echo: " + line);
      }
    } catch (IOException e) {
      // In a simulation, handle abrupt disconnects cleanly
    }
  }

  public void stop() {
    this.running = false;
  }
}
