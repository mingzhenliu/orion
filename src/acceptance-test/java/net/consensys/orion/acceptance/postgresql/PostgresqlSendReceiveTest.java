/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package net.consensys.orion.acceptance.postgresql;

import static io.vertx.core.Vertx.vertx;
import static net.consensys.orion.acceptance.NodeUtils.assertTransaction;
import static net.consensys.orion.acceptance.NodeUtils.joinPathsAsTomlListEntry;
import static net.consensys.orion.acceptance.NodeUtils.sendTransaction;
import static net.consensys.orion.acceptance.NodeUtils.viewTransaction;
import static org.apache.tuweni.io.file.Files.copyResource;

import net.consensys.orion.acceptance.EthClientStub;
import net.consensys.orion.acceptance.NodeUtils;
import net.consensys.orion.cmd.Orion;
import net.consensys.orion.config.Config;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import org.apache.tuweni.junit.TempDirectory;
import org.apache.tuweni.junit.TempDirectoryExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(TempDirectoryExtension.class)
class PostgresqlSendReceiveTest {

  private static final String PK_2_B_64 = "Ko2bVqD+nNlNYL5EE7y3IdOnviftjiizpjRt+HTuFBs=";
  private static final String HOST_NAME = "127.0.0.1";
  private static final String JDBC_URL = "jdbc:postgresql://localhost:5432/%s?user=%s&password=%s";

  private Orion orionLauncher;
  private Vertx vertx;
  private HttpClient httpClient;
  private final String databaseUser = System.getenv("POSTGRES_USER");
  private final String databasePassword = System.getenv("POSTGRES_PASSWORD");
  private final String databaseName = System.getenv("POSTGRES_DB");

  @BeforeEach
  void setUp(@TempDirectory final Path tempDir) throws Exception {
    Assumptions.assumeTrue(
        databaseUser != null && databasePassword != null && databaseName != null,
        "PostgreSQL not configured");

    final String jdbcUrl = String.format(JDBC_URL, databaseName, databaseUser, databasePassword);

    try (final Connection conn = DriverManager.getConnection(jdbcUrl)) {
      final Statement st = conn.createStatement();
      st.executeUpdate("create table if not exists store(key char(60), value bytea, primary key(key))");
    }

    final Path key1pub = copyResource("key1.pub", tempDir.resolve("key1.pub"));
    final Path key1key = copyResource("key1.key", tempDir.resolve("key1.key"));
    final Path key2pub = copyResource("key2.pub", tempDir.resolve("key2.pub"));
    final Path key2key = copyResource("key2.key", tempDir.resolve("key2.key"));

    final Config config = NodeUtils.nodeConfig(
        tempDir,
        0,
        HOST_NAME,
        0,
        HOST_NAME,
        "node1",
        joinPathsAsTomlListEntry(key1pub, key2pub),
        joinPathsAsTomlListEntry(key1key, key2key),
        "off",
        "tofu",
        "tofu",
        "sql:" + jdbcUrl);

    vertx = vertx();
    orionLauncher = NodeUtils.startOrion(config);
    httpClient = vertx.createHttpClient();
  }

  @AfterEach
  void tearDown() {
    if (orionLauncher != null) {
      orionLauncher.stop();
    }
    if (vertx != null) {
      vertx.close();
    }
  }

  @Test
  void sendAndReceive() {
    final EthClientStub ethClientStub = NodeUtils.client(orionLauncher.clientPort(), httpClient);

    final String digest = sendTransaction(ethClientStub, PK_2_B_64, PK_2_B_64);
    final byte[] receivedPayload = viewTransaction(ethClientStub, PK_2_B_64, digest);

    assertTransaction(receivedPayload);
  }

}
