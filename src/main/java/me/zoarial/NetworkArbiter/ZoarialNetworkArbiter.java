package me.zoarial.NetworkArbiter;

import me.zoarial.NetworkArbiter.annotations.ZoarialNetworkObject;
import me.zoarial.NetworkArbiter.annotations.ZoarialObjectElement;
import me.zoarial.NetworkArbiter.exceptions.ArbiterException;
import me.zoarial.NetworkArbiter.exceptions.DuplicatePlacement;
import me.zoarial.NetworkArbiter.exceptions.NotANetworkObject;
import me.zoarial.NetworkArbiter.objectmaps.NetworkObjectMap;

import java.lang.reflect.Field;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;


public class ZoarialNetworkArbiter {
    private final Socket socket;
    private final NetworkObjectMap networkObjectMap;

    public ZoarialNetworkArbiter(Socket socket) {
        this.socket = socket;
        this.networkObjectMap = new NetworkObjectMap();
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
        HashMap<Integer, Field> fieldOrder = new HashMap<>();
        for(Field f : fields) {
            if(f.isAnnotationPresent(ZoarialObjectElement.class)) {
                ZoarialObjectElement objectElement = f.getAnnotation(ZoarialObjectElement.class);
                if(!fieldOrder.containsKey(objectElement.placement())) {
                    fieldOrder.put(objectElement.placement(), f);
                } else {
                    Field existingField = fieldOrder.get(objectElement.placement());
                    String str = "Duplicate placement found: " + objectElement.placement() + ". Existing: \"" + existingField + "\". New: \"" + f + "\".";
                    throw new DuplicatePlacement(str);
                }
            }
            //System.out.println(f);
        }

        // Sort the field order by the key (user provided placement)
        List<Map.Entry<Integer, Field>> sortedElements = fieldOrder.entrySet().stream().sorted(Comparator.comparingInt(Map.Entry::getKey)).collect(Collectors.toList());

        NetworkObjectMap.NetworkTypeLengthMap lengthMap = new NetworkObjectMap.NetworkTypeLengthMap();
        NetworkObjectMap.NetworkTypeMap typeMap = new NetworkObjectMap.NetworkTypeMap();
        AtomicInteger totalLen = new AtomicInteger();
        // Print list
        sortedElements.forEach(e -> System.out.println("Entry " + e.getKey() + ": " + e.getValue()));

        sortedElements.forEach(e -> {
            Class<?> aClass = e.getValue().getType();
            if(networkObjectMap.containsKey(aClass)) {
                NetworkObjectMap.NetworkObjectEnum networkType = networkObjectMap.get(aClass);
                int typeLen = lengthMap.get(networkType);
                System.out.println(e + " (len: " + typeLen + "): " + networkType);
                totalLen.addAndGet(typeLen + 1);
            } else {
                String str = "Object is not in map: " + aClass;
                System.out.println(str);
                throw new NotANetworkObject(str);
            }

        });

        System.out.println("The total length for this object is: " + totalLen);

        int i = 0;
        byte[] buf = new byte[totalLen.get()];
        System.out.println("Working loop: \n");
        for (Map.Entry<Integer, Field> element : sortedElements) {

            byte[] tmp = getByteList(obj, element.getValue(), networkObjectMap.get(element.getValue().getType()));
            NetworkObjectMap.NetworkObjectEnum networkType = networkObjectMap.get(element.getValue().getType());

            System.out.println("Header (" + networkType + "):");
            buf[i] = typeMap.get(networkType);
            System.out.println(buf[i]);
            i++;

            System.arraycopy(tmp, 0, buf, i, tmp.length);
            i += tmp.length;

            System.out.println("Data");
            for (byte b :
                    tmp) {
                System.out.println(b);
            }
            System.out.println();
        }

        System.out.println("Final array: \n");
        for (byte b :
                buf) {
            System.out.println(b);
        }


    }

    public void receiveObject(Object obj) {

    }

    public byte[] getByteList(Object obj, Field f, NetworkObjectMap.NetworkObjectEnum type) {
        try {
            return switch (type) {
                case BYTE -> new byte[]{(byte) f.get(obj)};
                case SHORT -> ByteBuffer.allocate(2).putShort((short)f.get(obj)).array();
                case INT -> ByteBuffer.allocate(4).putInt((int)f.get(obj)).array();
                case LONG -> ByteBuffer.allocate(8).putLong((long)f.get(obj)).array();
                case UUID -> {
                    UUID uuid = (UUID)f.get(UUID.class);
                    yield ByteBuffer.allocate(16).putLong(uuid.getMostSignificantBits()).putLong(uuid.getLeastSignificantBits()).array();
                }
                //TODO: Decided if i want to add JSONObject to hold the JSON data
                case BOOLEAN -> ByteBuffer.allocate(1).put((byte)((Boolean)f.get(obj) ? 1 : 0)).array();
                case STRING -> {
                    byte[] strArr = ((String)f.get(obj)).getBytes();
                    int len = strArr.length;
                    byte[] byteArr = new byte[strArr.length + 1];
                    System.arraycopy(strArr, 0, byteArr, 0, len);
                    byteArr[len] = 0; // Set null byte
                    yield byteArr;
                }
                case ARRAY -> new byte[0];
            };
        } catch (Exception ignored) {
            throw new RuntimeException("Type wasn't expected type: ");
        }
    }

}
