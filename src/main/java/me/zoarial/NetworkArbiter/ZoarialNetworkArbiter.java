package me.zoarial.NetworkArbiter;

import me.zoarial.NetworkArbiter.annotations.ZoarialNetworkObject;
import me.zoarial.NetworkArbiter.annotations.ZoarialObjectElement;
import me.zoarial.NetworkArbiter.exceptions.ArbiterException;
import me.zoarial.NetworkArbiter.exceptions.DuplicatePlacement;
import me.zoarial.NetworkArbiter.exceptions.NotANetworkObject;

import java.lang.reflect.Field;
import java.net.Socket;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ZoarialNetworkArbiter {
    private final Socket socket;

    public ZoarialNetworkArbiter(Socket socket) {
        this.socket = socket;
    }

    public void sendObject(Object obj) throws ArbiterException {
        if(Objects.isNull(obj)) {
            throw new NotANetworkObject("Unable to send a null object");
        }

        Class<?> c = obj.getClass();
        if(!c.isAnnotationPresent(ZoarialNetworkObject.class)) {
            throw new NotANetworkObject("The class " + c.getSimpleName() + " is not a " + ZoarialNetworkObject.class.getSimpleName());
        }

        System.out.println();
        System.out.println();
        Field[] fields = c.getFields();
        HashMap<Integer, String> fieldOrder = new HashMap<>();
        for(Field f : fields) {
            if(f.isAnnotationPresent(ZoarialObjectElement.class)) {
                ZoarialObjectElement objectElement = f.getAnnotation(ZoarialObjectElement.class);
                if(!fieldOrder.containsKey(objectElement.placement())) {
                    fieldOrder.put(objectElement.placement(), f.getName());
                } else {
                    String existingField = fieldOrder.get(objectElement.placement());
                    String str = "Duplicate placement found: " + objectElement.placement() + ". Existing: \"" + existingField + "\". New: \"" + f.getName() + "\".";
                    throw new DuplicatePlacement(str);
                }
            }
            //System.out.println(f);
        }

        // Sort the field order by the key (user provided placement)
        fieldOrder.entrySet().stream().sorted(Comparator.comparingInt(Map.Entry::getKey)).forEach(e -> System.out.println("Entry " + e.getKey() + ": " + e.getValue()));

    }

    public void receiveObject(Object obj) {

    }

}
