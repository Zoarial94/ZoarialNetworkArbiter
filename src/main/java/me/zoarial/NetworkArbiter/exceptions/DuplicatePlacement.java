package me.zoarial.NetworkArbiter.exceptions;


import me.zoarial.NetworkArbiter.annotations.ZoarialObjectElement;

import java.lang.reflect.Field;

public class DuplicatePlacement extends ArbiterException {
    public DuplicatePlacement(String str) {
        super(str);
    }

    public DuplicatePlacement(ZoarialObjectElement existingElement, ZoarialObjectElement duplicateElement) {
        super("Duplicate placement found: " + existingElement.placement() + ". Existing: \"" + existingElement + "\". New: \"" + duplicateElement + "\".");

    }
}
