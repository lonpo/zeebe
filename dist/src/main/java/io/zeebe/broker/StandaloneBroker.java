/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker;

import static java.lang.Runtime.getRuntime;

import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.util.FileUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

public class StandaloneBroker {
  private static final CountDownLatch WAITING_LATCH = new CountDownLatch(1);
  private static String tempFolder;
  private static BrokerCfg config;

  public static void main(final String[] args) throws Exception {
    if (args.length == 1) {
      System.setProperty("spring.config.additional-location", "file:" + args[0]);
    }

    SpringApplication.run(SpringBootConifgurationLoader.class, args);

    final Broker broker;

    if (args.length == 1) {
      broker = createBrokerFromConfiguration(args);
    } else {
      broker = createDefaultBrokerInTempDirectory();
    }

    broker.start();
    getRuntime()
        .addShutdownHook(
            new Thread("Broker close Thread") {
              @Override
              public void run() {
                try {
                  if (broker != null) {
                    broker.close();
                  }
                } finally {
                  deleteTempDirectory();
                }
              }
            });
    WAITING_LATCH.await();
  }

  private static Broker createBrokerFromConfiguration(final String[] args) {
    String basePath = System.getProperty("basedir");

    if (basePath == null) {
      basePath = Paths.get(".").toAbsolutePath().normalize().toString();
    }

    return new Broker(config, basePath, null);
  }

  private static Broker createDefaultBrokerInTempDirectory() {
    Loggers.SYSTEM_LOGGER.info("No configuration file specified. Using default configuration.");

    try {
      tempFolder = Files.createTempDirectory("zeebe").toAbsolutePath().normalize().toString();
      return new Broker(config, tempFolder, null);
    } catch (final IOException e) {
      throw new RuntimeException("Could not start broker", e);
    }
  }

  private static void deleteTempDirectory() {
    if (tempFolder != null) {
      try {
        FileUtil.deleteFolder(tempFolder);
      } catch (final IOException e) {
        e.printStackTrace();
      }
    }
  }

  @SpringBootApplication
  public static class SpringBootConifgurationLoader implements CommandLineRunner {
    @Autowired BrokerCfg config;

    @Override
    public void run(final String... args) throws Exception {
      StandaloneBroker.config = config;
    }
  }
}
