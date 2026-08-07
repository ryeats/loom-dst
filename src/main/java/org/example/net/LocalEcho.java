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

/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalIoHandler;
import io.netty.channel.local.LocalServerChannel;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class LocalEcho implements Closeable {

  private final EventLoopGroup serverGroup;
  private final EventLoopGroup clientGroup;
  private final LocalAddress addr;

  public LocalEcho(String port) {
    // Address to bind on / connect to.
    addr = new LocalAddress(port);
    serverGroup =
        new MultiThreadIoEventLoopGroup(
            Executors.newVirtualThreadPerTaskExecutor(), LocalIoHandler.newFactory());
    clientGroup =
        new MultiThreadIoEventLoopGroup(
            Executors.newVirtualThreadPerTaskExecutor(), LocalIoHandler.newFactory());
  }

  public void server() throws InterruptedException, IOException {
    // Note that we can use any event loop to ensure certain local channels
    // are handled by the same event loop thread which drives a certain socket channel
    // to reduce the communication latency between socket channels and local channels.
    ServerBootstrap sb = new ServerBootstrap();
    sb.group(serverGroup)
        .channel(LocalServerChannel.class)
        .handler(
            new ChannelInitializer<LocalServerChannel>() {
              @Override
              public void initChannel(LocalServerChannel ch) throws Exception {
                //                ch.pipeline().addLast(new LoggingHandler(LogLevel.INFO));
              }
            })
        .childHandler(
            new ChannelInitializer<LocalChannel>() {
              @Override
              public void initChannel(LocalChannel ch) throws Exception {
                ch.pipeline()
                    .addLast(
                        //                        new LoggingHandler(LogLevel.INFO),
                        new LocalEchoServerHandler());
              }
            });

    // Start the server.
    sb.bind(addr).sync();
  }

  public void client(String msg, Consumer<String> responseHandler)
      throws InterruptedException, IOException {
    Bootstrap cb = new Bootstrap();
    cb.group(clientGroup)
        .channel(LocalChannel.class)
        .handler(
            new ChannelInitializer<LocalChannel>() {
              @Override
              public void initChannel(LocalChannel ch) throws Exception {
                ch.pipeline()
                    .addLast(
                        //                        new LoggingHandler(LogLevel.INFO),
                        new LocalEchoClientHandler(responseHandler));
              }
            });
    // Start the client.
    Channel ch = cb.connect(addr).sync().channel();

    // Read commands from the stdin.
    //      System.out.println("Enter text (quit to end)");
    ChannelFuture lastWriteFuture = null;
    BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
    //      for (;;) {
    //        msg = in.readLine();
    //        if (msg == null || "quit".equalsIgnoreCase(msg)) {
    //          break;
    //        }

    // Sends the received line to the server.
    lastWriteFuture = ch.writeAndFlush(msg);
    //      }
    //      Thread.sleep(1000);
    // Wait until all messages are flushed before closing the channel.
    if (lastWriteFuture != null) {
      lastWriteFuture.awaitUninterruptibly();
    }
  }

  @Override
  public void close() throws IOException {
    serverGroup.shutdownGracefully();
    clientGroup.shutdownGracefully();
  }

  public static void main(String[] args) throws Exception {
    try (LocalEcho localEcho = new LocalEcho("test")) {
      CompletableFuture<String> future = new CompletableFuture<>();
      localEcho.server();
      localEcho.client("testMsg", future::complete);
      System.out.println(future.get());
    }
  }
}
