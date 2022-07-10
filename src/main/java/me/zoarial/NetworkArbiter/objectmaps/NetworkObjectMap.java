package me.zoarial.NetworkArbiter.objectmaps;

import java.util.*;

public class NetworkObjectMap extends HashMap<Class<?>, NetworkObjectMap.NetworkObjectEnum> {

    public NetworkObjectMap() {
        super();

        put(Byte.class, NetworkObjectEnum.BYTE);
        put(Short.class, NetworkObjectEnum.SHORT);
        put(Integer.class, NetworkObjectEnum.INT);
        put(Long.class, NetworkObjectEnum.LONG);
        put(Boolean.class, NetworkObjectEnum.BOOLEAN);
        put(String.class, NetworkObjectEnum.STRING);
        put(UUID.class, NetworkObjectEnum.UUID);

        put(byte.class, get(Byte.class));
        put(short.class, get(Short.class));
        put(int.class, get(Integer.class));
        put(long.class, get(Long.class));
        put(boolean.class, get(Boolean.class));
    }


    public static class NetworkTypeMap extends HashMap<NetworkObjectEnum, Byte> {
        private static final AutoIncrementInt inc = new AutoIncrementInt();

        public NetworkTypeMap() {
            super();

            put(NetworkObjectEnum.BYTE, inc.get());
            put(NetworkObjectEnum.SHORT, inc.get());
            put(NetworkObjectEnum.INT, inc.get());
            put(NetworkObjectEnum.LONG, inc.get());
            put(NetworkObjectEnum.BOOLEAN, inc.get());
            put(NetworkObjectEnum.STRING, inc.get());
            put(NetworkObjectEnum.UUID, inc.get());
            put(NetworkObjectEnum.ARRAY, inc.get());
        }

    }

    public static class NetworkTypeLengthMap extends HashMap<NetworkObjectEnum, Byte> {

        public NetworkTypeLengthMap() {
            super();

            put(NetworkObjectEnum.BYTE, (byte)1);
            put(NetworkObjectEnum.SHORT, (byte)2);
            put(NetworkObjectEnum.INT, (byte)4);
            put(NetworkObjectEnum.LONG, (byte)8);
            put(NetworkObjectEnum.BOOLEAN, (byte)1);
            put(NetworkObjectEnum.STRING, (byte)-1);
            put(NetworkObjectEnum.UUID, (byte)16);
            put(NetworkObjectEnum.ARRAY, (byte)-1);
        }
    }

    public enum NetworkObjectEnum {
        BYTE,
        SHORT,
        INT,
        LONG,

        BOOLEAN,
        STRING,
        UUID,
        ARRAY,
    }

}

class Pair
{
    // Return an immutable singleton map containing only the specified
    // key-value pair mapping
    public static <T, U> Map<T, U> of(T first, U second) {
        return Collections.singletonMap(first, second);
    }
}

class AutoIncrementInt {
    private byte i = 1;

    public byte get() {
        return i++;
    }
}

