/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.util.health;

public interface FailureListener {
  void onFailure();

  void onRecovered();

  static FailureListener withListeners(final Runnable onFailure, final Runnable onRecovered) {
    return new FailureListener() {
      @Override
      public void onFailure() {
        onFailure.run();
      }

      @Override
      public void onRecovered() {
        onRecovered.run();
      }
    };
  }
}
