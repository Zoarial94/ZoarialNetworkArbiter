package me.zoarial.networkArbiter.exceptions

import me.zoarial.networkArbiter.annotations.ZoarialObjectElement

class DuplicatePlacement : ArbiterException {
    constructor(str: String?) : super(str) {}
    constructor(existingElement: ZoarialObjectElement, duplicateElement: ZoarialObjectElement) : super("Duplicate placement found: " + existingElement.placement + ". Existing: \"" + existingElement + "\". New: \"" + duplicateElement + "\".") {}
}