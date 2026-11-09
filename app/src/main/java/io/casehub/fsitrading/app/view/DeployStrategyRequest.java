package io.casehub.fsitrading.app.view;

import io.casehub.fsitrading.model.StrategyType;

public record DeployStrategyRequest(String name, StrategyType strategyType) {}
