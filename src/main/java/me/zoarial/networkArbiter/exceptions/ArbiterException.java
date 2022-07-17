package me.zoarial.networkArbiter.exceptions;

public abstract class ArbiterException extends RuntimeException {
    public ArbiterException() {
        super();
    }

    public ArbiterException(String str) {
        super(str);
    }
}
