package me.zoarial.NetworkArbiter;

import me.zoarial.NetworkArbiter.annotations.ZoarialNetworkObject;
import me.zoarial.NetworkArbiter.annotations.ZoarialObjectElement;
import me.zoarial.NetworkArbiter.exceptions.ArbiterException;
import me.zoarial.NetworkArbiter.exceptions.DuplicatePlacement;
import me.zoarial.NetworkArbiter.exceptions.MismatchedObject;
import me.zoarial.NetworkArbiter.exceptions.NotANetworkObject;
import me.zoarial.NetworkArbiter.objectmaps.NetworkObjectMap;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;


public class ZoarialNetworkArbiter {
    private final Socket socket;
    private final NetworkObjectMap networkObjectMap;

    private final short NETWORK_ARBITER_VERSION = 1;

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
        NetworkObjectMap.TypeToRawMap typeMap = new NetworkObjectMap.TypeToRawMap();
        AtomicInteger totalLen = new AtomicInteger(7);
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

        // 3 for ZNA, 1 for Network Version, 2 for length, 1 for 255 as the last byte
        int i = 6;
        byte[] buf = new byte[totalLen.get()];
        System.arraycopy("ZNA".getBytes(), 0, buf, 0, 3);
        buf[3] = NETWORK_ARBITER_VERSION;
        System.arraycopy(ByteBuffer.allocate(2).putShort((short)sortedElements.size()).array(), 0, buf, 4, 2);
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
            for (byte b : tmp) {
                System.out.print(b);
            }
            System.out.println("\n");
        }

        buf[buf.length - 1] = (byte) 255;


        System.out.println("Final array:");
        for (byte b :
                buf) {
            System.out.println(b);
        }

        BufferedOutputStream rawOut = null;
        try {
            rawOut = new BufferedOutputStream(socket.getOutputStream());
            DataOutputStream out = new DataOutputStream(rawOut);
            out.write(buf);
            System.out.flush();
            out.flush();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    public <T> Optional<T> receiveObject(Class<T> clazz) {
        BufferedInputStream rawIn;
        byte[] buf = new byte[256];
        int len;
        try {
            rawIn = new BufferedInputStream(socket.getInputStream());
            DataInputStream in = new DataInputStream(rawIn);
            len = in.read(buf);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        System.out.println("Received object...");
        for (int i = 0; i < len; i++) {
            System.out.println("Byte: " + buf[i]);
        }


        List<NetworkObjectMap.NetworkObjectEnum> objectNetworkRepresentation = getNetworkRepresentation(clazz);
        List<NetworkObjectMap.NetworkObjectEnum> actualNetworkRepresentation = decodeNetworkObject(new ByteArrayInputStream(buf));

        if(!objectNetworkRepresentation.equals(actualNetworkRepresentation)) {
            throw new MismatchedObject("Objects don't match");
        } else {
            System.out.println("Objects match signature");
        }

        return Optional.of(createObject(clazz, new ByteArrayInputStream(buf)));
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

    public List<NetworkObjectMap.NetworkObjectEnum> getNetworkRepresentation(Class<?> clazz) throws ArbiterException {
        Field[] fields = clazz.getFields();
        HashMap<Integer, Field> fieldOrder = new HashMap<>();
        ArrayList<NetworkObjectMap.NetworkObjectEnum> ret = new ArrayList<>();

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
        }

        // Sort the field order by the key (user provided placement)
        List<Map.Entry<Integer, Field>> sortedElements = fieldOrder.entrySet().stream().sorted(Comparator.comparingInt(Map.Entry::getKey)).collect(Collectors.toList());

        // Print list
        sortedElements.forEach(e -> System.out.println("Entry " + e.getKey() + ": " + e.getValue()));

        sortedElements.forEach(e -> {
            Class<?> aClass = e.getValue().getType();
            if(networkObjectMap.containsKey(aClass)) {
                NetworkObjectMap.NetworkObjectEnum networkType = networkObjectMap.get(aClass);
                ret.add(networkType);
            } else {
                String str = "Object is not in map: " + aClass;
                System.out.println(str);
                throw new NotANetworkObject(str);
            }

        });

       return ret;
    }


    public List<NetworkObjectMap.NetworkObjectEnum> decodeNetworkObject(InputStream inputStream) throws ArbiterException {
        DataInputStream in = new DataInputStream(inputStream);
        byte[] buf = new byte[256];
        int read;
        int objectLen;
        ArrayList<NetworkObjectMap.NetworkObjectEnum> ret = new ArrayList<>();

        NetworkObjectMap.NetworkTypeLengthMap lengthMap = new NetworkObjectMap.NetworkTypeLengthMap();
        NetworkObjectMap.RawToTypeMap rawToTypeMap = new NetworkObjectMap.RawToTypeMap();

        try {
            read = in.read(buf, 0, 3);
            if(Arrays.compare(buf, 0, 3, buf, 0, 3) != 0) {
                throw new NotANetworkObject("Not ZNA");
            }
            if(in.readByte() != NETWORK_ARBITER_VERSION) {
                throw new NotANetworkObject("Incorrect Arbiter Object Version");
            }
            objectLen = in.readUnsignedShort();

            for(int i = 0; i < objectLen; i++) {
                byte rawType = in.readByte();
                if(!rawToTypeMap.containsKey(rawType)) {
                    throw new NotANetworkObject("Invalid raw type: " + rawType);
                }

                NetworkObjectMap.NetworkObjectEnum type = rawToTypeMap.get(rawType);
                in.readNBytes(buf, 0, lengthMap.get(type));
                ret.add(type);
            }
            if(in.readByte() != (byte)255) {
                throw new NotANetworkObject("Object doesn't end correctly.");
            }



        } catch (IOException e) {
            throw new RuntimeException(e);
        }


        return ret;
    }

    public <T> T createObject(Class<T> clazz, InputStream inputStream) {
        T obj;
        try {
            obj = clazz.getDeclaredConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }

        Field[] fields = clazz.getFields();
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
        }

        // Sort the field order by the key (user provided placement)
        List<Map.Entry<Integer, Field>> sortedElements = fieldOrder.entrySet().stream().sorted(Comparator.comparingInt(Map.Entry::getKey)).collect(Collectors.toList());

        DataInputStream in = new DataInputStream(inputStream);
        byte[] buf = new byte[256];
        int read;
        int objectLen;

        NetworkObjectMap.RawToTypeMap rawToTypeMap = new NetworkObjectMap.RawToTypeMap();

        try {
            read = in.read(buf, 0, 3);
            if(Arrays.compare(buf, 0, 3, buf, 0, 3) != 0) {
                throw new NotANetworkObject("Not ZNA");
            }
            if(in.readByte() != NETWORK_ARBITER_VERSION) {
                throw new NotANetworkObject("Incorrect Arbiter Object Version");
            }
            objectLen = in.readUnsignedShort();

            for(Map.Entry<Integer, Field> e : sortedElements) {
                byte rawType = in.readByte();
                NetworkObjectMap.NetworkObjectEnum type = rawToTypeMap.get(rawType);
                Field field = e.getValue();

                if(field.getType().isPrimitive()) {
                    switch (type) {
                        case BYTE -> field.setByte(obj, in.readByte());
                        case SHORT -> field.setShort(obj, in.readShort());
                        case INT -> field.setInt(obj, in.readInt());
                        case LONG -> field.setLong(obj, in.readLong());
                        case BOOLEAN -> field.setBoolean(obj, in.readBoolean());
                        default -> {}
                    }
                } else {
                    switch (type) {
                        case BYTE -> field.set(obj, in.readByte());
                        case SHORT -> field.set(obj, in.readShort());
                        case INT -> field.set(obj, in.readInt());
                        case LONG -> field.set(obj, in.readLong());
                        case UUID -> {
                        }
                        case BOOLEAN -> field.set(obj, in.readBoolean());
                        case STRING -> {
                        }
                        case ARRAY -> {
                        }
                    }
                }
            }

            // shouldn't be needed at this point
            if(in.readByte() != (byte)255) {
                throw new NotANetworkObject("Object doesn't end correctly.");
            }



        } catch (IOException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }


        return obj;
    }

}
