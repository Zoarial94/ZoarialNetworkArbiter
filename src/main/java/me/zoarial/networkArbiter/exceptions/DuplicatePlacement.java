package me.zoarial.networkArbiter.exceptions;


import me.zoarial.networkArbiter.annotations.ZoarialObjectElement;

public class DuplicatePlacement extends ArbiterException {
    public DuplicatePlacement(String str) {
        super(str);
    }

    public DuplicatePlacement(ZoarialObjectElement existingElement, ZoarialObjectElement duplicateElement) {
        super("Duplicate placement found: " + existingElement.placement() + ". Existing: \"" + existingElement + "\". New: \"" + duplicateElement + "\".");

    }
}
