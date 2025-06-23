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

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import java.io.Closeable;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.example.DeterministicExecutor;
import org.example.SchedulableVirtualThreadFactory;

public final class NettyEchoServer extends ChannelInboundHandlerAdapter implements Closeable {

  private final Executor executor;
  private MultiThreadIoEventLoopGroup workerGroup;
  private final int port;
  private volatile ChannelFuture f;

  public NettyEchoServer(Executor executor, int port) {
    this.port = port;
    this.executor = executor;
  }

  public Future<Void> start() throws InterruptedException {
    this.workerGroup = new MultiThreadIoEventLoopGroup(executor, NioIoHandler.newFactory());
    ServerBootstrap bootstrap = new ServerBootstrap();
    bootstrap
        .group(workerGroup)
        .channel(NioServerSocketChannel.class)
        .childHandler(
            new ChannelInitializer<SocketChannel>() {
              @Override
              protected void initChannel(SocketChannel ch) {
                ChannelPipeline p = ch.pipeline();
                p.addLast(new StringDecoder(StandardCharsets.UTF_8));
                p.addLast(new StringEncoder(StandardCharsets.UTF_8));
                p.addLast(
                    new SimpleChannelInboundHandler<String>() {
                      @Override
                      protected void channelRead0(ChannelHandlerContext ctx, String msg) {
                        ctx.writeAndFlush(msg.toUpperCase()); // Capitalize and echo
                      }
                    });
              }
            });

    f = bootstrap.bind(port);
    return f;
  }

  public boolean isStarted() {
    if (f != null) {
      return f.isDone();
    }
    return false;
  }

  @Override
  public void close() {
    if (workerGroup != null) {
      workerGroup.shutdownGracefully();
    }
  }

  public static void main2(String[] args) throws Exception {
    ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    try (NettyEchoServer server = new NettyEchoServer(executor, 5050)) {
      server.start();
      try (NettyEchoClient client =
          new NettyEchoClient(
              executor,
              "localhost",
              5050,
              (nec, s) -> {
                System.out.println("Received from server: " + s);
              })) {
        client.start();

        client.sendMessage("hello netty").get();
        Thread.sleep(1);
      }
    }
  }

  public static void main(String[] args) throws Exception {
    DeterministicExecutor de = new DeterministicExecutor(new Random(4321));
    SchedulableVirtualThreadFactory tf = new SchedulableVirtualThreadFactory(de);
    ExecutorService executor = Executors.newThreadPerTaskExecutor(tf);
    try (NettyEchoServer server = new NettyEchoServer(executor, 5050)) {
      executor.submit(
          () -> {
            try {
              server.start();
            } catch (InterruptedException e) {
              throw new RuntimeException(e);
            }
          });
      de.drain();
      de.drain();
      de.drain();
      executor.submit(
          () -> {
            try (NettyEchoClient client =
                new NettyEchoClient(
                    executor,
                    "localhost",
                    5050,
                    (nec, s) -> {
                      System.out.println("Received from server: " + s);
                    })) {
              client.start();

              Future<Void> blah = client.sendMessage("hello netty");
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
          });
      de.drain();
      Thread.sleep(2000);
    }
    executor.close();
    de.drain();
    de.drain();
  }
}
