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

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;

public final class NettyEchoClient extends ChannelInboundHandlerAdapter implements AutoCloseable {

  private final BiConsumer<NettyEchoClient, String> responseHandler;
  private final Executor executor;
  private MultiThreadIoEventLoopGroup group;
  private final String host;
  private final int port;
  private Channel channel;
  private ChannelFuture channelFuture;

  public NettyEchoClient(
      Executor executor,
      String host,
      int port,
      BiConsumer<NettyEchoClient, String> responseHandler) {
    this.host = host;
    this.port = port;
    this.responseHandler = responseHandler;
    this.executor = executor;
  }

  public Future<Void> start() throws InterruptedException {
    group = new MultiThreadIoEventLoopGroup(executor, NioIoHandler.newFactory());
    Bootstrap bootstrap = new Bootstrap();
    bootstrap
        .group(group)
        .channel(NioSocketChannel.class)
        .handler(
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
                        responseHandler.accept(NettyEchoClient.this, msg);
                      }
                    });
              }
            });

    channelFuture = bootstrap.connect(host, port);
    channelFuture.addListener((_) -> channel = channelFuture.channel());
    return channelFuture;
  }

  public Future<Void> sendMessage(String message) {
    if (channel == null) {
      try {
        channelFuture.get();
        channel = channelFuture.channel();
      } catch (InterruptedException | ExecutionException e) {
        throw new RuntimeException(e);
      }
    }
    return channel.writeAndFlush(message);
  }

  @Override
  public void close() {
    if (channel != null) {
      channel.close();
    }
    if (group != null) {
      group.shutdownGracefully();
    }
  }
}
