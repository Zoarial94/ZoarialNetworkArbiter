package me.zoarial.NetworkArbiter.exceptions;

public abstract class ArbiterException extends RuntimeException {
    public ArbiterException() {
        super();
    }

    public ArbiterException(String str) {
        super(str);
    }
}
