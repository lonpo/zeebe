/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.util.health;

import io.zeebe.util.sched.ActorControl;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;

/** Healthy only if all components are healthy */
public class CriticalComponentsHealthMonitor implements HealthMonitor {
  private static final Duration HEALTH_MONITORING_PERIOD = Duration.ofSeconds(60);
  private final Map<String, HealthMonitorable> monitoredComponents = new HashMap<>();
  private final Map<String, HealthStatus> componentHealth = new HashMap<>();
  private final AtomicReference<HealthStatus> healthStatus = new AtomicReference<>();
  private final ActorControl actor;
  private final Logger log;
  private FailureListener failureListener;

  public CriticalComponentsHealthMonitor(final ActorControl actor, final Logger log) {
    this.actor = actor;
    this.log = log;
    this.failureListener = null;
  }

  public void startMonitoring() {
    actor.runAtFixedRate(HEALTH_MONITORING_PERIOD, this::updateHealth);
  }

  @Override
  public void monitorComponent(final String componentName) {
    actor.run(() -> componentHealth.put(componentName, HealthStatus.UNHEALTHY));
  }

  @Override
  public void removeComponent(final String componentName) {
    actor.run(
        () -> {
          monitoredComponents.remove(componentName);
          componentHealth.remove(componentName);
        });
  }

  @Override
  public void registerComponent(final String componentName, final HealthMonitorable component) {
    actor.run(
        () -> {
          monitoredComponents.put(componentName, component);
          component.addFailureListener(
              FailureListener.withListeners(
                  () -> onComponentFailure(componentName),
                  () -> onComponentRecovered(componentName)));
          componentHealth.put(componentName, component.getHealthStatus());
        });
  }

  @Override
  public HealthStatus getHealthStatus() {
    return healthStatus.get();
  }

  @Override
  public void addFailureListener(final FailureListener failureListener) {
    actor.run(() -> this.failureListener = failureListener);
  }

  private void onComponentFailure(final String componentName) {
    actor.call(
        () -> {
          log.error("{} failed, marking it as unhealthy", componentName);
          componentHealth.put(componentName, HealthStatus.UNHEALTHY);
          reCalculateHealth();
        });
  }

  private void onComponentRecovered(final String componentName) {
    actor.call(
        () -> {
          log.error("{} failed, marking it as unhealthy", componentName);
          componentHealth.put(componentName, HealthStatus.UNHEALTHY);
        });
  }

  private void updateHealth() {
    componentHealth.keySet().stream()
        .forEach(component -> componentHealth.put(component, getHealth(component)));
    reCalculateHealth();
  }

  private void reCalculateHealth() {
    final boolean healthy =
        componentHealth.values().stream()
            .allMatch(healthStatus -> healthStatus == HealthStatus.HEALTHY);
    healthStatus.set(healthy ? HealthStatus.HEALTHY : HealthStatus.UNHEALTHY);
    if (!healthy) {
      notifyFailureListener();
    }
  }

  private void notifyFailureListener() {
    if (this.failureListener != null) {
      failureListener.onFailure();
    }
  }

  private HealthStatus getHealth(final String componentName) {
    final HealthMonitorable component = monitoredComponents.get(componentName);
    if (component != null) {
      return component.getHealthStatus();
    }
    return HealthStatus.UNHEALTHY;
  }
}
