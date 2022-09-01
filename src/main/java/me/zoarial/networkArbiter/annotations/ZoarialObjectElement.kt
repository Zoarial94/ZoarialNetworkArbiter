package me.zoarial.networkArbiter.annotations

import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

@Retention(RetentionPolicy.RUNTIME)
@Target(AnnotationTarget.FIELD)
annotation class ZoarialObjectElement(val optional: Boolean = false, val placement: Int)