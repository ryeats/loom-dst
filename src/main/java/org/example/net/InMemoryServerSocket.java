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
import java.net.SocketAddress;

// Explicitly pass your custom implementation to the protected super-constructor
public class InMemoryServerSocket extends ServerSocket {

  // Explicit constructor required for createServerSocket() unbound calls
  public InMemoryServerSocket() throws IOException {
    super(new InMemorySocketImpl());
  }

  public InMemoryServerSocket(int port) throws IOException {
    super(new InMemorySocketImpl());
    bind(new java.net.InetSocketAddress(port));
  }

  @Override
  public void bind(SocketAddress endpoint, int backlog) throws IOException {
    // Forward the internal initialization logic down to our custom SocketImpl
    // via the standard protected impl initialization hook
    super.bind(endpoint, backlog);
  }

  @Override
  public Socket accept() throws IOException {
    if (!isBound()) throw new java.net.SocketException("Socket is not bound");

    // Pass an uninitialized in-memory client socket to hold the accepted data
    InMemorySocket clientSocket = new InMemorySocket();
    implAccept(clientSocket);
    return clientSocket;
  }

  public static void main(String[] args) throws Exception {
    // Spin up Server
    Thread serverThread =
        new Thread(
            () -> {
              try (ServerSocket server = new InMemoryServerSocket(8080);
                  Socket client = server.accept();
                  BufferedReader in =
                      new BufferedReader(new InputStreamReader(client.getInputStream()));
                  PrintWriter out = new PrintWriter(client.getOutputStream(), true)) {

                String line = in.readLine();
                out.println("Echo: " + line);
              } catch (Exception e) {
                e.printStackTrace();
              }
            });
    serverThread.start();

    // Run Client
    try (Socket socket = new InMemorySocket("localhost", 8080);
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

      out.println("Hello Java 25 Isolated Memory Pipe!");
      System.out.println("Server Response: " + in.readLine());
    }
  }
}
