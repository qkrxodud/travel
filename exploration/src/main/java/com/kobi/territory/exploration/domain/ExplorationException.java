package com.kobi.territory.exploration.domain;

import com.kobi.territory.common.error.TerritoryException;

public class ExplorationException extends TerritoryException {

    private final ExplorationError error;

    public ExplorationException(ExplorationError error, String message) {
        super(error.name(), error.kind(), message);
        this.error = error;
    }

    public ExplorationError error() {
        return error;
    }
}
