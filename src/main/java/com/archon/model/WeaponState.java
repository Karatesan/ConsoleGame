package com.archon.model;

/**
 * Encapsulates dynamic combat weapon readiness (e.g. nocked arrows).
 */
public final class WeaponState {
    private boolean nocked;

    public boolean isNocked() {
        return nocked;
    }

    public void nock() {
        this.nocked = true;
    }

    public void release() {
        this.nocked = false;
    }
}
