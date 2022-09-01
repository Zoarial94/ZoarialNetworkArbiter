package me.zoarial.networkArbiter.exceptions

abstract class ArbiterException : RuntimeException {
    constructor() : super() {}
    constructor(str: String?) : super(str) {}
}