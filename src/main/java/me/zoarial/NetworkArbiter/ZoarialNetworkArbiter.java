package me.zoarial.NetworkArbiter;

import me.zoarial.NetworkArbiter.annotations.ZoarialNetworkObject;
import me.zoarial.NetworkArbiter.annotations.ZoarialObjectElement;
import me.zoarial.NetworkArbiter.exceptions.ArbiterException;
import me.zoarial.NetworkArbiter.exceptions.DuplicatePlacement;
import me.zoarial.NetworkArbiter.exceptions.MismatchedObject;
import me.zoarial.NetworkArbiter.exceptions.NotANetworkObject;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

enum NetworkElementTypeEnum {
    BYTE,
    SHORT,
    INT,
    LONG,

    BOOLEAN,
    STRING,
    UUID,
    ARRAY,
}

class AutoIncrementInt {
    private byte i = 1;

    public byte get() {
        return i++;
    }
}

public class ZoarialNetworkArbiter {
    private final Socket socket;

    private final HashMap<Class<?>, NetworkElementTypeEnum> classToNetworkElementMap = new HashMap<>();
    private final HashMap<NetworkElementTypeEnum, Class<?>> networkElementToClassMap = new HashMap<>();

    private final HashMap<NetworkElementTypeEnum, Byte> networkElementToByteMap = new HashMap<>();
    private final HashMap<Byte, NetworkElementTypeEnum> byteToNetworkElementMap = new HashMap<>();

    private final HashMap<NetworkElementTypeEnum, Byte> networkElementLengthMap = new HashMap<>();

    private final AutoIncrementInt inc = new AutoIncrementInt();

    private final short NETWORK_ARBITER_VERSION = 1;

    public ZoarialNetworkArbiter(Socket socket) {
        this.socket = socket;

        // TODO: Clean up and make these maps static at some point
        classToNetworkElementMap.put(Byte.class, NetworkElementTypeEnum.BYTE);
        classToNetworkElementMap.put(Short.class, NetworkElementTypeEnum.SHORT);
        classToNetworkElementMap.put(Integer.class, NetworkElementTypeEnum.INT);
        classToNetworkElementMap.put(Long.class, NetworkElementTypeEnum.LONG);
        classToNetworkElementMap.put(Boolean.class, NetworkElementTypeEnum.BOOLEAN);
        classToNetworkElementMap.put(String.class, NetworkElementTypeEnum.STRING);
        classToNetworkElementMap.put(UUID.class, NetworkElementTypeEnum.UUID);

        classToNetworkElementMap.put(byte.class, classToNetworkElementMap.get(Byte.class));
        classToNetworkElementMap.put(short.class, classToNetworkElementMap.get(Short.class));
        classToNetworkElementMap.put(int.class, classToNetworkElementMap.get(Integer.class));
        classToNetworkElementMap.put(long.class, classToNetworkElementMap.get(Long.class));
        classToNetworkElementMap.put(boolean.class, classToNetworkElementMap.get(Boolean.class));

        networkElementToByteMap.put(NetworkElementTypeEnum.BYTE, inc.get());
        networkElementToByteMap.put(NetworkElementTypeEnum.SHORT, inc.get());
        networkElementToByteMap.put(NetworkElementTypeEnum.INT, inc.get());
        networkElementToByteMap.put(NetworkElementTypeEnum.LONG, inc.get());
        networkElementToByteMap.put(NetworkElementTypeEnum.BOOLEAN, inc.get());
        networkElementToByteMap.put(NetworkElementTypeEnum.STRING, inc.get());
        networkElementToByteMap.put(NetworkElementTypeEnum.UUID, inc.get());
        networkElementToByteMap.put(NetworkElementTypeEnum.ARRAY, inc.get());

        byteToNetworkElementMap.put(networkElementToByteMap.get(NetworkElementTypeEnum.BYTE), NetworkElementTypeEnum.BYTE);
        byteToNetworkElementMap.put(networkElementToByteMap.get(NetworkElementTypeEnum.SHORT), NetworkElementTypeEnum.SHORT);
        byteToNetworkElementMap.put(networkElementToByteMap.get(NetworkElementTypeEnum.INT), NetworkElementTypeEnum.INT);
        byteToNetworkElementMap.put(networkElementToByteMap.get(NetworkElementTypeEnum.LONG), NetworkElementTypeEnum.LONG);
        byteToNetworkElementMap.put(networkElementToByteMap.get(NetworkElementTypeEnum.BOOLEAN), NetworkElementTypeEnum.BOOLEAN);
        byteToNetworkElementMap.put(networkElementToByteMap.get(NetworkElementTypeEnum.STRING), NetworkElementTypeEnum.STRING);
        byteToNetworkElementMap.put(networkElementToByteMap.get(NetworkElementTypeEnum.UUID), NetworkElementTypeEnum.UUID);
        byteToNetworkElementMap.put(networkElementToByteMap.get(NetworkElementTypeEnum.ARRAY), NetworkElementTypeEnum.ARRAY);

        networkElementLengthMap.put(NetworkElementTypeEnum.BYTE, (byte)1);
        networkElementLengthMap.put(NetworkElementTypeEnum.SHORT, (byte)2);
        networkElementLengthMap.put(NetworkElementTypeEnum.INT, (byte)4);
        networkElementLengthMap.put(NetworkElementTypeEnum.LONG, (byte)8);
        networkElementLengthMap.put(NetworkElementTypeEnum.BOOLEAN, (byte)1);
        networkElementLengthMap.put(NetworkElementTypeEnum.UUID, (byte)16);
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

        NetworkObjectStructure objectStructure = getObjectStructure(obj);
        List<NetworkElement> basicElements = objectStructure.getBasicElements();


        AtomicInteger totalLen = new AtomicInteger(7);

        basicElements.forEach(e -> {
            NetworkElementTypeEnum type = e.getType();
            totalLen.getAndAdd(networkElementLengthMap.get(type) + 1);

        });

        System.out.println("The total length for this object is: " + totalLen);

        // 3 for ZNA, 1 for Network Version, 2 for length, 1 for 255 as the last byte
        int i = 6;
        byte[] buf = new byte[totalLen.get()];
        System.arraycopy("ZNA".getBytes(), 0, buf, 0, 3);
        buf[3] = NETWORK_ARBITER_VERSION;
        System.arraycopy(ByteBuffer.allocate(2).putShort((short)basicElements.size()).array(), 0, buf, 4, 2);
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


        List<NetworkElementTypeEnum> objectNetworkRepresentation = getNetworkRepresentation(clazz);
        List<NetworkElementTypeEnum> actualNetworkRepresentation = decodeNetworkObject(new ByteArrayInputStream(buf));

        if(!objectNetworkRepresentation.equals(actualNetworkRepresentation)) {
            throw new MismatchedObject("Objects don't match");
        } else {
            System.out.println("Objects match signature");
        }

        return Optional.of(createObject(clazz, new ByteArrayInputStream(buf)));
    }

    public byte[] getByteList(Object obj, NetworkElement element) {
        NetworkElementTypeEnum type = element.getType();
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

    public List<NetworkElementTypeEnum> getNetworkRepresentation(Class<?> clazz) throws ArbiterException {
        Field[] fields = clazz.getFields();
        HashMap<Integer, Field> fieldOrder = new HashMap<>();
        ArrayList<NetworkElementTypeEnum> ret = new ArrayList<>();

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
                NetworkElementTypeEnum networkType = classToNetworkElementMap.get(aClass);
                ret.add(networkType);
            } else {
                String str = "Object is not in map: " + aClass;
                System.out.println(str);
                throw new NotANetworkObject(str);
            }

        });

       return ret;
    }


    public List<NetworkElementTypeEnum> decodeNetworkObject(InputStream inputStream) throws ArbiterException {
        DataInputStream in = new DataInputStream(inputStream);
        byte[] buf = new byte[256];
        int read;
        int objectLen;
        ArrayList<NetworkElementTypeEnum> ret = new ArrayList<>();


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

                NetworkElementTypeEnum type = byteToNetworkElementMap.get(rawType);
                in.readNBytes(buf, 0, networkElementLengthMap.get(type));
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
        if(!clazz.isAnnotationPresent(ZoarialNetworkObject.class)) {
            throw new NotANetworkObject("Object is not a network object");
        }
        T obj;
        try {
            obj = clazz.getDeclaredConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }

        NetworkObjectStructure objectStructure = getObjectStructure(obj);
        List<NetworkElement> basicElements = objectStructure.getBasicElements();

        DataInputStream in = new DataInputStream(inputStream);
        byte[] buf = new byte[256];
        int read;
        int objectLen;

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

            if(objectLen != basicElements.size()) {
                throw new MismatchedObject("Element length of receiving object does not equal given object");
            }

            for(NetworkElement e : basicElements) {
                byte rawType = in.readByte();
                NetworkElementTypeEnum type = byteToNetworkElementMap.get(rawType);
                Field field = e.getField();

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
                        case UUID -> {
                        }
                        case BOOLEAN -> field.set(obj, in.readBoolean());
                        case STRING -> {
                        }
                        case ARRAY -> {
                        }
                        default -> throw new NotANetworkObject("Unsupported object");
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

    public static class NetworkElement {
        private final int index;
        private final NetworkElementTypeEnum type;
        private final Field field;

        NetworkElement(int index, NetworkElementTypeEnum type, Field field) {
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

        public NetworkElementTypeEnum getType() {
            return type;
        }
    }

    public static class NetworkObjectStructure {
        private final List<NetworkElement> basicElements;
        private final List<NetworkElement> advancedElements;

        public NetworkObjectStructure(List<NetworkElement> basicElements, List<NetworkElement> advancedElements) {
            this.advancedElements = advancedElements;
            this.basicElements = basicElements;
        }

        List<NetworkElement> getBasicElements() {
            return basicElements;
        }

        List<NetworkElement> getAdvancedElements() {
            return advancedElements;
        }
    }

    /**
     *
     * @param obj
     * @return A sorted list of {@link NetworkElement NetworkElements}
     */
    public NetworkObjectStructure getObjectStructure(Object obj) {
        if(!obj.getClass().isAnnotationPresent(ZoarialNetworkObject.class)) {
            throw new NotANetworkObject("Object is not a ZoarialNetworkObject.");
        }

        Class<?> objectClass = obj.getClass();
        Field[] fields = objectClass.getFields();
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
                NetworkElementTypeEnum networkType = classToNetworkElementMap.get(fieldClass);
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

        return new NetworkObjectStructure(basicList.stream().sorted(Comparator.comparingInt(NetworkElement::getIndex)).collect(Collectors.toList()), advancedList.stream().sorted(Comparator.comparingInt(NetworkElement::getIndex)).collect(Collectors.toList()));
    }

}
