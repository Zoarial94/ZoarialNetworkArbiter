package me.zoarial.networkArbiter

enum class NetworkElementType {
    BYTE, SHORT, INT, LONG, BOOLEAN, UUID, STRING, ARRAY, SUB_OBJECT;

    fun getID(): Byte {
        return (ordinal + 1).toByte()
    }
}