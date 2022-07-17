package me.zoarial.networkArbiter;

import me.zoarial.networkArbiter.annotations.ZoarialNetworkObject;
import me.zoarial.networkArbiter.annotations.ZoarialObjectElement;
import me.zoarial.networkArbiter.exceptions.ArbiterException;
import me.zoarial.networkArbiter.exceptions.DuplicatePlacement;
import me.zoarial.networkArbiter.exceptions.MismatchedObject;
import me.zoarial.networkArbiter.exceptions.NotANetworkObject;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;


public class ZoarialNetworkArbiter {

    private static ZoarialNetworkArbiter singleton;

    private static final HashMap<Class<?>, NetworkElementType> classToNetworkElementMap = new HashMap<>();
    private static final HashMap<NetworkElementType, Class<?>> networkElementToClassMap = new HashMap<>();

    private static final HashMap<NetworkElementType, Byte> networkElementToByteMap = new HashMap<>();
    private static final HashMap<Byte, NetworkElementType> byteToNetworkElementMap = new HashMap<>();

    private static final HashMap<NetworkElementType, Byte> networkElementLengthMap = new HashMap<>();

    private static final AutoIncrementInt inc = new AutoIncrementInt();
    static class AutoIncrementInt {
        private byte i = 1;

        public byte get() {
            return i++;
        }
    }

    private final short NETWORK_ARBITER_VERSION = 1;

    public static ZoarialNetworkArbiter getInstance() {
        if(singleton == null) {
            singleton = new ZoarialNetworkArbiter();
        }
        return singleton;
    }

    private ZoarialNetworkArbiter() {

        // TODO: Clean up and make these maps static at some point
        classToNetworkElementMap.put(Byte.class, NetworkElementType.BYTE);
        classToNetworkElementMap.put(Short.class, NetworkElementType.SHORT);
        classToNetworkElementMap.put(Integer.class, NetworkElementType.INT);
        classToNetworkElementMap.put(Long.class, NetworkElementType.LONG);
        classToNetworkElementMap.put(Boolean.class, NetworkElementType.BOOLEAN);
        classToNetworkElementMap.put(String.class, NetworkElementType.STRING);
        classToNetworkElementMap.put(UUID.class, NetworkElementType.UUID);

        classToNetworkElementMap.put(byte.class, classToNetworkElementMap.get(Byte.class));
        classToNetworkElementMap.put(short.class, classToNetworkElementMap.get(Short.class));
        classToNetworkElementMap.put(int.class, classToNetworkElementMap.get(Integer.class));
        classToNetworkElementMap.put(long.class, classToNetworkElementMap.get(Long.class));
        classToNetworkElementMap.put(boolean.class, classToNetworkElementMap.get(Boolean.class));

        networkElementToByteMap.put(NetworkElementType.BYTE, inc.get());
        networkElementToByteMap.put(NetworkElementType.SHORT, inc.get());
        networkElementToByteMap.put(NetworkElementType.INT, inc.get());
        networkElementToByteMap.put(NetworkElementType.LONG, inc.get());
        networkElementToByteMap.put(NetworkElementType.BOOLEAN, inc.get());
        networkElementToByteMap.put(NetworkElementType.STRING, inc.get());
        networkElementToByteMap.put(NetworkElementType.UUID, inc.get());
        networkElementToByteMap.put(NetworkElementType.ARRAY, inc.get());

        byteToNetworkElementMap.put(networkElementToByteMap.get(NetworkElementType.BYTE), NetworkElementType.BYTE);
        byteToNetworkElementMap.put(networkElementToByteMap.get(NetworkElementType.SHORT), NetworkElementType.SHORT);
        byteToNetworkElementMap.put(networkElementToByteMap.get(NetworkElementType.INT), NetworkElementType.INT);
        byteToNetworkElementMap.put(networkElementToByteMap.get(NetworkElementType.LONG), NetworkElementType.LONG);
        byteToNetworkElementMap.put(networkElementToByteMap.get(NetworkElementType.BOOLEAN), NetworkElementType.BOOLEAN);
        byteToNetworkElementMap.put(networkElementToByteMap.get(NetworkElementType.STRING), NetworkElementType.STRING);
        byteToNetworkElementMap.put(networkElementToByteMap.get(NetworkElementType.UUID), NetworkElementType.UUID);
        byteToNetworkElementMap.put(networkElementToByteMap.get(NetworkElementType.ARRAY), NetworkElementType.ARRAY);

        networkElementLengthMap.put(NetworkElementType.BYTE, (byte)1);
        networkElementLengthMap.put(NetworkElementType.SHORT, (byte)2);
        networkElementLengthMap.put(NetworkElementType.INT, (byte)4);
        networkElementLengthMap.put(NetworkElementType.LONG, (byte)8);
        networkElementLengthMap.put(NetworkElementType.BOOLEAN, (byte)1);
        networkElementLengthMap.put(NetworkElementType.UUID, (byte)16);
    }

    public void sendObject(Object obj, Socket socket) throws ArbiterException {
        if(Objects.isNull(obj)) {
            throw new NotANetworkObject("Unable to send a null object");
        }

        Class<?> c = obj.getClass();
        if(!c.isAnnotationPresent(ZoarialNetworkObject.class)) {
            throw new NotANetworkObject("The class " + c.getSimpleName() + " is not a " + ZoarialNetworkObject.class.getSimpleName());
        }

        System.out.println();
        System.out.println();

        NetworkObject objectStructure = getObjectStructure(obj.getClass());
        List<NetworkElement> basicElements = objectStructure.getBasicElements();
        List<NetworkElement> advancedElements = objectStructure.getAdvancedElements();


        AtomicInteger totalLen = new AtomicInteger(7);

        basicElements.forEach(e -> {
            NetworkElementType type = e.getType();
            totalLen.getAndAdd(networkElementLengthMap.get(type) + 1);

        });

        advancedElements.forEach(e-> {
            NetworkElementType type = e.getType();
                try {
                totalLen.getAndAdd(switch (type) {
                    case STRING -> ((String)e.getField().get(obj)).length() + 2;
                    case ARRAY -> ((List<?>)e.getField().get(obj)).size();
                    default -> throw new RuntimeException("Only advanced elements should be here. Got: " + type);
                });
                } catch (IllegalAccessException ex) {
                    throw new RuntimeException("Something went wrong when getting length for advanced elements.");
                }
        });

        System.out.println("The total length for this object is: " + totalLen);

        // 3 for ZNA, 1 for Network Version, 2 for length, 1 for 255 as the last byte
        int i = 6;
        byte[] buf = new byte[totalLen.get()];
        System.arraycopy("ZNA".getBytes(), 0, buf, 0, 3);
        buf[3] = NETWORK_ARBITER_VERSION;
        System.arraycopy(ByteBuffer.allocate(2).putShort((short)(basicElements.size() + advancedElements.size())).array(), 0, buf, 4, 2);
        System.out.println("Working loop: \n");
        for (NetworkElement element : basicElements) {

            byte[] tmp = getByteList(obj, element);

            System.out.println("Header (" + element.getType() + "):");
            buf[i] = networkElementToByteMap.get(element.getType());
            System.out.println(buf[i]);
            i++;

            System.arraycopy(tmp, 0, buf, i, tmp.length);
            i += tmp.length;

            System.out.println("Data");
            for (byte b : tmp) {
                System.out.print(b + " ");
            }
            System.out.println("\n");
        }

        // Header first, and then data at the end of the object
        for (NetworkElement element : advancedElements) {
            System.out.println("Advanced elements headers:");

            byte[] tmp = getByteList(obj, element);
            NetworkElementType elementType = element.getType();

            System.out.println("Header (" + elementType + "):");
            buf[i] = networkElementToByteMap.get(elementType);
            System.out.println(buf[i]);
            i++;

            System.out.println("Length: " + tmp.length);
            buf[i] = (byte)tmp.length;
            i++;

            System.out.println("\n");
        }

        // Put the data at the end of the object
        for (NetworkElement element : advancedElements) {
            System.out.println("Placing data in object:");

            byte[] tmp = getByteList(obj, element);
            NetworkElementType elementType = element.getType();

            System.out.print("Header (" + elementType + "): ");
            System.out.println(buf[i]);
            System.out.println("Length: " + tmp.length);

            System.arraycopy(tmp, 0, buf, i, tmp.length);
            i += tmp.length;

            System.out.println("Data");
            for (byte b : tmp) {
                System.out.print(b + " ");
            }
            System.out.println("\n");
        }

        buf[buf.length - 1] = (byte) 255;


        System.out.println("Final array:");
        for (byte b :
                buf) {
            System.out.println(b);
        }

        BufferedOutputStream rawOut;
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

    public <T> Optional<T> receiveObject(Class<T> clazz, Socket socket) {
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


        NetworkObject objectNetworkRepresentation = getObjectStructure(clazz);
        List<NetworkElementType> networkRepresentation = decodeNetworkObject(new ByteArrayInputStream(buf));

        if(!objectNetworkRepresentation.equalsStructure(networkRepresentation)) {
            throw new MismatchedObject("Objects don't match");
        } else {
            System.out.println("Objects match signature");
        }

        return Optional.of(createObject(clazz, new ByteArrayInputStream(buf)));
    }

    public byte[] getByteList(Object obj, NetworkElement element) {
        NetworkElementType type = element.getType();
        Field f = element.getField();
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
                case STRING ->  ((String)f.get(obj)).getBytes();
                case ARRAY -> new byte[0];
            };
        } catch (Exception ignored) {
            throw new RuntimeException("Type wasn't expected type: ");
        }
    }

    public List<NetworkElementType> getNetworkRepresentation(Class<?> clazz) throws ArbiterException {
        Field[] fields = clazz.getFields();
        HashMap<Integer, Field> fieldOrder = new HashMap<>();
        ArrayList<NetworkElementType> ret = new ArrayList<>();

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
            if(classToNetworkElementMap.containsKey(aClass)) {
                NetworkElementType networkType = classToNetworkElementMap.get(aClass);
                ret.add(networkType);
            } else {
                String str = "Object is not in map: " + aClass;
                System.out.println(str);
                throw new NotANetworkObject(str);
            }

        });

       return ret;
    }


    public List<NetworkElementType> decodeNetworkObject(InputStream inputStream) throws ArbiterException {
        DataInputStream in = new DataInputStream(inputStream);
        byte[] buf = new byte[256];
        int read;
        int objectLen;
        ArrayList<NetworkElementType> ret = new ArrayList<>();
        int advancedElementDataLen = 0;

        boolean readingAdvanced = false;

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
                if(!byteToNetworkElementMap.containsKey(rawType)) {
                    throw new NotANetworkObject("Invalid raw type: " + rawType);
                }

                NetworkElementType type = byteToNetworkElementMap.get(rawType);
                switch(type) {
                    case BYTE, SHORT, INT, LONG, BOOLEAN, UUID -> {
                        if(readingAdvanced) {
                            throw new NotANetworkObject("Incorrect object structure order from network.");
                        }
                        in.readNBytes(buf, 0, networkElementLengthMap.get(type));
                        ret.add(type);
                    }
                    case STRING, ARRAY -> {
                        if(!readingAdvanced) {
                            readingAdvanced = true;
                        }
                        switch (type) {
                            case STRING -> {
                                // frikin java and their always signed-ness
                                short strLen = (short)((short)in.readByte() & (short)0xFF);
                                advancedElementDataLen += strLen;
                                ret.add(type);
                            }
                            case ARRAY -> throw new RuntimeException("Not implemented");
                        }
                    }
                    default -> throw new RuntimeException("Forgot to implement: " + type);
                }
            }
            System.out.println("Network Structure: ");
            for(NetworkElementType e : ret) {
                System.out.println(e);
            }
            in.readNBytes(advancedElementDataLen);
            if(in.readByte() != (byte)255) {
                throw new NotANetworkObject("Object doesn't end correctly.");
            }



        } catch (IOException e) {
            throw new RuntimeException(e);
        }


        return ret;
    }

    /**
     *
     * @param clazz
     * @param inputStream
     * @return
     * @param <T>
     */
    private <T> T createObject(Class<T> clazz, InputStream inputStream) {
        if(!clazz.isAnnotationPresent(ZoarialNetworkObject.class)) {
            throw new NotANetworkObject("Object is not a network object");
        }
        T obj;
        try {
            obj = clazz.getDeclaredConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }

        NetworkObject objectStructure = getObjectStructure(obj.getClass());
        List<NetworkElement> basicElements = objectStructure.getBasicElements();
        List<NetworkElement> advancedElements = objectStructure.getAdvancedElements();

        DataInputStream in = new DataInputStream(inputStream);
        byte[] buf = new byte[256];
        int read;
        int objectLen;
        int[] advancedElementLengths = new int[advancedElements.size()];

        try {
            read = in.read(buf, 0, 3);
            if(Arrays.compare(buf, 0, 3, buf, 0, 3) != 0) {
                throw new NotANetworkObject("Not ZNA");
            }
            if(in.readByte() != NETWORK_ARBITER_VERSION) {
                throw new NotANetworkObject("Incorrect Arbiter Object Version");
            }
            objectLen = in.readUnsignedShort();
            System.out.println("Object length: " + objectLen);

            if(objectLen != basicElements.size() + advancedElements.size()) {
                throw new MismatchedObject("Element length of receiving object does not equal given object");
            }

            for(NetworkElement e : basicElements) {
                byte rawType = in.readByte();
                NetworkElementType type = byteToNetworkElementMap.get(rawType);
                Field field = e.getField();

                if(e.getType() != type) {
                    throw new MismatchedObject("Expected type " + e.getType() + ". Got: " + type);
                }

                if(field.getType().isPrimitive()) {
                    switch (type) {
                        case BYTE -> field.setByte(obj, in.readByte());
                        case SHORT -> field.setShort(obj, in.readShort());
                        case INT -> field.setInt(obj, in.readInt());
                        case LONG -> field.setLong(obj, in.readLong());
                        case BOOLEAN -> field.setBoolean(obj, in.readBoolean());
                        default -> throw new NotANetworkObject("Unsupported primitive");
                    }
                } else {
                    switch (type) {
                        case BYTE -> field.set(obj, in.readByte());
                        case SHORT -> field.set(obj, in.readShort());
                        case INT -> field.set(obj, in.readInt());
                        case LONG -> field.set(obj, in.readLong());
                        case UUID -> throw new RuntimeException("Not implemented");
                        case BOOLEAN -> field.set(obj, in.readBoolean());
                        default -> throw new NotANetworkObject("Unsupported object");
                    }
                }
            }

            for (int i = 0; i < advancedElements.size(); i++) {
                NetworkElement e = advancedElements.get(i);
                byte rawType = in.readByte();
                NetworkElementType type = byteToNetworkElementMap.get(rawType);
                Field field = e.getField();

                if (field.getType().isPrimitive()) {
                    throw new NotANetworkObject("Unsupported primitive");
                } else {
                    switch (type) {
                        case STRING -> advancedElementLengths[i] = (int)in.readByte() & 0xFF;
                        case ARRAY -> throw new RuntimeException("Not implemented");
                        default -> throw new NotANetworkObject("Unsupported object");
                    }
                }
            }

            for (int i = 0; i < advancedElements.size(); i++) {
                NetworkElement e = advancedElements.get(i);
                Field field = e.getField();

                if (field.getType().isPrimitive()) {
                    throw new NotANetworkObject("Unsupported primitive");
                } else {
                    switch (e.getType()) {
                        case STRING -> e.getField().set(obj, new String((in.readNBytes(advancedElementLengths[i]))));
                        case ARRAY -> throw new RuntimeException("Not implemented");
                        default -> throw new NotANetworkObject("Unsupported object");
                    }
                }
            }



            if(in.readByte() != (byte)255) {
                throw new NotANetworkObject("Object doesn't end correctly.");
            }



        } catch (IOException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }


        return obj;
    }

    public static class NetworkElement {
        private final int index;
        private final NetworkElementType type;
        private final Field field;

        NetworkElement(int index, NetworkElementType type, Field field) {
            this.index = index;
            this.type = type;
            this.field = field;
        }

        public Field getField() {
            return field;
        }

        public int getIndex() {
            return index;
        }

        public NetworkElementType getType() {
            return type;
        }
    }

    public static class NetworkObject {
        private final List<NetworkElement> basicElements;
        private final List<NetworkElement> advancedElements;

        public NetworkObject(List<NetworkElement> basicElements, List<NetworkElement> advancedElements) {
            if(basicElements == null || advancedElements == null) {
                throw new RuntimeException("Cannot create with null lists.");
            }
            this.advancedElements = advancedElements;
            this.basicElements = basicElements;
        }

        List<NetworkElement> getBasicElements() {
            return basicElements;
        }

        List<NetworkElement> getAdvancedElements() {
            return advancedElements;
        }

        boolean equalsStructure(List<NetworkElementType> other) {
            if(Objects.isNull(other)) {
                throw new RuntimeException("Cannot compare to null object");
            }
            List<NetworkElementType> tmp = new ArrayList<>();
            tmp.addAll(basicElements.stream().map(NetworkElement::getType).collect(Collectors.toList()));
            tmp.addAll(advancedElements.stream().map(NetworkElement::getType).collect(Collectors.toList()));
            return tmp.equals(other);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            NetworkObject that = (NetworkObject) o;
            return basicElements.equals(that.basicElements) && advancedElements.equals(that.advancedElements);
        }

        @Override
        public int hashCode() {
            return Objects.hash(basicElements, advancedElements);
        }
    }

    /**
     *
     * @param clazz
     * @return A sorted list of {@link NetworkElement NetworkElements}
     */
    public NetworkObject getObjectStructure(Class<?> clazz) {
        if(!clazz.isAnnotationPresent(ZoarialNetworkObject.class)) {
            throw new NotANetworkObject("Object is not a ZoarialNetworkObject.");
        }

        Field[] fields = clazz.getFields();
        ArrayList<NetworkElement> basicList = new ArrayList<>();
        ArrayList<NetworkElement> advancedList = new ArrayList<>();

        for(Field f : fields) {
            if(f.isAnnotationPresent(ZoarialObjectElement.class)) {
                ZoarialObjectElement objectElementAnnotation = f.getAnnotation(ZoarialObjectElement.class);
                Class<?> fieldClass = f.getType();
                int placement = objectElementAnnotation.placement();

                if(!classToNetworkElementMap.containsKey(fieldClass)) {
                    String str = "Object is not in map: " + fieldClass;
                    System.out.println(str);
                    throw new NotANetworkObject(str);
                }
                NetworkElementType networkType = classToNetworkElementMap.get(fieldClass);
                List<NetworkElement> correctList;

                correctList = switch(networkType) {
                    case BYTE, SHORT, INT, LONG, BOOLEAN -> basicList;
                    case UUID, STRING, ARRAY -> advancedList;
                };

                if(correctList.stream().filter(e -> e.getIndex() == placement).findFirst().isEmpty()) {
                    correctList.add(new NetworkElement(placement, networkType, f));
                } else {
                    NetworkElement networkElement = correctList.get(placement);
                    throw new DuplicatePlacement(networkElement.getField().getAnnotation(ZoarialObjectElement.class), objectElementAnnotation);
                }
            }
        }

        // Print list
        //sortedElements.forEach(e -> System.out.println("Entry " + e.getIndex() + ": " + e.getType()));

        return new NetworkObject(basicList.stream().sorted(Comparator.comparingInt(NetworkElement::getIndex)).collect(Collectors.toList()), advancedList.stream().sorted(Comparator.comparingInt(NetworkElement::getIndex)).collect(Collectors.toList()));
    }

}
