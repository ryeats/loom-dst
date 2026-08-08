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

import java.io.*;
import java.net.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class InMemorySocketImpl extends SocketImpl {
  private BlockingQueue<Integer> inboundQueue;
  private BlockingQueue<Integer> outboundQueue;
  private final BlockingQueue<InMemorySocketImpl> connectionQueue = new LinkedBlockingQueue<>();
  private boolean isServer = false;

  // Stream implementations mapping Queue operations to standard I/O
  private InputStream inputStream;
  private OutputStream outputStream;

  @Override
  protected void create(boolean stream) throws IOException {
    // Initialization handled during connect/bind
  }

  @Override
  protected void listen(int backlog) throws IOException {
    // Handled by the ServerSocket binding process
  }

  @Override
  protected void bind(InetAddress host, int port) throws IOException {
    this.port = port;
    this.isServer = true;
    InMemoryNetworkRegistry.bind(port, this);
  }

  @Override
  protected void connect(String host, int port) throws IOException {
    connect(InetAddress.getByName(host), port);
  }

  @Override
  protected void connect(InetAddress address, int port) throws IOException {
    InMemorySocketImpl serverImpl = InMemoryNetworkRegistry.fetchServer(port);
    if (serverImpl == null) {
      throw new ConnectException("Connection refused to port " + port);
    }

    // Create buffers for bidirectional pipe
    this.inboundQueue = new LinkedBlockingQueue<>();
    this.outboundQueue = new LinkedBlockingQueue<>();

    // Create the twin socket representation for the server side
    InMemorySocketImpl serverAcceptedPeer = new InMemorySocketImpl();
    serverAcceptedPeer.inboundQueue = this.outboundQueue; // Server reads what client writes
    serverAcceptedPeer.outboundQueue = this.inboundQueue; // Server writes what client reads

    // Handshake: Hand the peer socket over to the server's accept queue
    serverImpl.connectionQueue.add(serverAcceptedPeer);
  }

  @Override
  protected void accept(SocketImpl s) throws IOException {
    try {
      // Block until a client attempts to connect to our central registry
      InMemorySocketImpl clientPeer = this.connectionQueue.take();

      // Transfer the memory queues directly to the accepted client socket impl
      InMemorySocketImpl target = (InMemorySocketImpl) s;
      target.inboundQueue = clientPeer.inboundQueue;
      target.outboundQueue = clientPeer.outboundQueue;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new java.net.SocketException("Accept interrupted");
    }
  }

  //  @Override
  //  protected InputStream getInputStream() throws IOException {
  //    if (inputStream == null) {
  //      inputStream = new InputStream() {
  //        @Override
  //        public int read() throws IOException {
  //          try {
  //            // Blocking read from memory queue
  //            return inboundQueue.take();
  //          } catch (InterruptedException e) {
  //            Thread.currentThread().interrupt();
  //            return -1;
  //          }
  //        }
  //      };
  //    }
  //    return inputStream;
  //  }
  @Override
  protected InputStream getInputStream() throws IOException {
    if (inputStream == null) {
      inputStream =
          new InputStream() {
            @Override
            public int read() throws IOException {
              try {
                return inboundQueue.take();
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException("Read interrupted");
              }
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
              if (b == null) throw new NullPointerException();
              if (off < 0 || len < 0 || len > b.length - off) throw new IndexOutOfBoundsException();
              if (len == 0) return 0;

              try {
                // Block until at least one byte is available
                int firstByte = inboundQueue.take();
                if (firstByte == -1) return -1; // End of stream signal
                b[off] = (byte) firstByte;

                // Drain any remaining bytes currently waiting in the buffer without blocking again
                int bytesRead = 1;
                while (bytesRead < len) {
                  Integer nextByte = inboundQueue.poll(); // Non-blocking check
                  if (nextByte == null) break;
                  if (nextByte == -1) return bytesRead; // Hit EOF marker
                  b[off + bytesRead] = (nextByte.byteValue());
                  bytesRead++;
                }
                return bytesRead;
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException("Bulk read interrupted");
              }
            }
          };
    }
    return inputStream;
  }

  @Override
  protected OutputStream getOutputStream() throws IOException {
    if (outputStream == null) {
      outputStream =
          new OutputStream() {
            @Override
            public void write(int b) throws IOException {
              try {
                outboundQueue.put(b & 0xFF);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Write interrupted");
              }
            }
          };
    }
    return outputStream;
  }

  @Override
  protected void close() throws IOException {
    if (isServer) {
      InMemoryNetworkRegistry.unbind(this.port);
    }
    // Signal EOF to streams by feeding termination flags if needed
  }

  // Boilerplate overrides required by SocketImpl base abstraction
  @Override
  protected int available() throws IOException {
    return inboundQueue.size();
  }

  @Override
  public void setOption(int optID, Object value) throws SocketException {}

  @Override
  public Object getOption(int optID) throws SocketException {
    return null;
  }

  @Override
  protected void connect(SocketAddress address, int timeout) throws IOException {
    connect(((InetSocketAddress) address).getAddress(), ((InetSocketAddress) address).getPort());
  }

  @Override
  protected void sendUrgentData(int data) throws IOException {}
}
