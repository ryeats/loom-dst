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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryNetworkRegistry {
  // Maps a port number to the ServerSocketImpl listening on it
  private static final Map<Integer, InMemorySocketImpl> registry = new ConcurrentHashMap<>();

  public static void bind(int port, InMemorySocketImpl impl) {
    if (registry.putIfAbsent(port, impl) != null) {
      throw new RuntimeException("Port already in use: " + port);
    }
  }

  public static void unbind(int port) {
    registry.remove(port);
  }

  public static InMemorySocketImpl fetchServer(int port) {
    return registry.get(port);
  }
}
