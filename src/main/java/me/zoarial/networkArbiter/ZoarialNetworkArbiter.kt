package me.zoarial.networkArbiter

import me.zoarial.networkArbiter.annotations.ZoarialNetworkObject
import me.zoarial.networkArbiter.annotations.ZoarialObjectElement
import me.zoarial.networkArbiter.exceptions.ArbiterException
import me.zoarial.networkArbiter.exceptions.DuplicatePlacement
import me.zoarial.networkArbiter.exceptions.MismatchedObject
import me.zoarial.networkArbiter.exceptions.NotANetworkObject
import java.io.*
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.net.Socket
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import java.util.stream.Collectors

object ZoarialNetworkArbiter {

    private val classToNetworkElementMap = HashMap<Class<*>?, NetworkElementType?>()
    private val networkElementToClassMap = HashMap<NetworkElementType, Class<*>>()
    private val networkElementToByteMap = HashMap<NetworkElementType?, Byte>()
    private val byteToNetworkElementMap = HashMap<Byte?, NetworkElementType>()
    private val networkElementLengthMap = HashMap<NetworkElementType?, Byte>()
    private val inc = AutoIncrementInt()

    internal class AutoIncrementInt {
        private var i: Byte = 1
        fun get(): Byte {
            return i++
        }
    }

    private const val NETWORK_ARBITER_VERSION: Short = 1

    init {

        // TODO: Clean up and make these maps static at some point
        classToNetworkElementMap[Byte::class.javaObjectType] = NetworkElementType.BYTE
        classToNetworkElementMap[Short::class.javaObjectType] = NetworkElementType.SHORT
        classToNetworkElementMap[Int::class.javaObjectType] = NetworkElementType.INT
        classToNetworkElementMap[Long::class.javaObjectType] = NetworkElementType.LONG
        classToNetworkElementMap[Boolean::class.javaObjectType] = NetworkElementType.BOOLEAN
        classToNetworkElementMap[String::class.javaObjectType] = NetworkElementType.STRING
        classToNetworkElementMap[UUID::class.javaObjectType] = NetworkElementType.UUID
        classToNetworkElementMap[Byte::class.javaPrimitiveType] = classToNetworkElementMap[Byte::class.javaObjectType]
        classToNetworkElementMap[Short::class.javaPrimitiveType] = classToNetworkElementMap[Short::class.javaObjectType]
        classToNetworkElementMap[Int::class.javaPrimitiveType] = classToNetworkElementMap[Int::class.javaObjectType]
        classToNetworkElementMap[Long::class.javaPrimitiveType] = classToNetworkElementMap[Long::class.javaObjectType]
        classToNetworkElementMap[Boolean::class.javaPrimitiveType] = classToNetworkElementMap[Boolean::class.javaObjectType]
        networkElementToByteMap[NetworkElementType.BYTE] = inc.get()
        networkElementToByteMap[NetworkElementType.SHORT] = inc.get()
        networkElementToByteMap[NetworkElementType.INT] = inc.get()
        networkElementToByteMap[NetworkElementType.LONG] = inc.get()
        networkElementToByteMap[NetworkElementType.BOOLEAN] = inc.get()
        networkElementToByteMap[NetworkElementType.STRING] = inc.get()
        networkElementToByteMap[NetworkElementType.UUID] = inc.get()
        networkElementToByteMap[NetworkElementType.ARRAY] = inc.get()
        byteToNetworkElementMap[networkElementToByteMap[NetworkElementType.BYTE]] = NetworkElementType.BYTE
        byteToNetworkElementMap[networkElementToByteMap[NetworkElementType.SHORT]] = NetworkElementType.SHORT
        byteToNetworkElementMap[networkElementToByteMap[NetworkElementType.INT]] = NetworkElementType.INT
        byteToNetworkElementMap[networkElementToByteMap[NetworkElementType.LONG]] = NetworkElementType.LONG
        byteToNetworkElementMap[networkElementToByteMap[NetworkElementType.BOOLEAN]] = NetworkElementType.BOOLEAN
        byteToNetworkElementMap[networkElementToByteMap[NetworkElementType.STRING]] = NetworkElementType.STRING
        byteToNetworkElementMap[networkElementToByteMap[NetworkElementType.UUID]] = NetworkElementType.UUID
        byteToNetworkElementMap[networkElementToByteMap[NetworkElementType.ARRAY]] = NetworkElementType.ARRAY
        networkElementLengthMap[NetworkElementType.BYTE] = 1.toByte()
        networkElementLengthMap[NetworkElementType.SHORT] = 2.toByte()
        networkElementLengthMap[NetworkElementType.INT] = 4.toByte()
        networkElementLengthMap[NetworkElementType.LONG] = 8.toByte()
        networkElementLengthMap[NetworkElementType.BOOLEAN] = 1.toByte()
        networkElementLengthMap[NetworkElementType.UUID] = 16.toByte()
    }

    @Throws(ArbiterException::class)
    fun sendObject(obj: Any, socket: Socket) {
        if (Objects.isNull(obj)) {
            throw NotANetworkObject("Unable to send a null object")
        }
        val c: Class<*> = obj.javaClass
        if (!c.isAnnotationPresent(ZoarialNetworkObject::class.java)) {
            throw NotANetworkObject("The class " + c.simpleName + " is not a " + ZoarialNetworkObject::class.java.simpleName)
        }
        println()
        println()
        val objectStructure = getObjectStructure(obj.javaClass)
        val basicElements = objectStructure.basicElements
        val advancedElements = objectStructure.advancedElements
        val totalLen = AtomicInteger(7)
        basicElements.forEach(Consumer { e: NetworkElement ->
            val type = e.type
            var sizeToAdd = 1
            try {
                if (!e.isOptional || e.field[obj] != null) {
                    sizeToAdd += networkElementLengthMap[type]!!.toInt()
                }
            } catch (ex: IllegalAccessException) {
                throw RuntimeException(ex)
            }
            totalLen.getAndAdd(sizeToAdd)
        })
        advancedElements.forEach(Consumer { e: NetworkElement ->
            val type = e.type
            try {
                totalLen.getAndAdd(when (type) {
                    NetworkElementType.STRING -> (e.field[obj] as String).length + 2
                    NetworkElementType.ARRAY -> (e.field[obj] as List<*>).size
                    else -> throw RuntimeException("Only advanced elements should be here. Got: $type")
                })
            } catch (ex: IllegalAccessException) {
                throw RuntimeException("Something went wrong when getting length for advanced elements.")
            }
        })
        println("The total length for this object is: $totalLen")

        // 3 for ZNA, 1 for Network Version, 2 for length, 1 for 255 as the last byte
        var i = 6
        val buf = ByteArray(totalLen.get())
        System.arraycopy("ZNA".toByteArray(), 0, buf, 0, 3)
        buf[3] = NETWORK_ARBITER_VERSION.toByte()
        System.arraycopy(ByteBuffer.allocate(2).putShort((basicElements.size + advancedElements.size).toShort()).array(), 0, buf, 4, 2)
        println("Working loop: \n")
        for (element in basicElements) {
            var optionalPresent: Boolean = try {
                element.field[obj] != null
            } catch (e: IllegalAccessException) {
                throw RuntimeException(e)
            }
            var tmp: ByteArray = if (!element.isOptional || optionalPresent) {
                getByteList(obj, element)
            } else {
                ByteArray(0)
            }
            println("Header (" + element.type + "):")
            var header = networkElementToByteMap[element.type]!!
            if (element.isOptional) {
                header = (header.toInt() or (1 shl 7)).toByte()
                if (optionalPresent) {
                    header = (header.toInt() or (1 shl 6)).toByte()
                }
            }
            buf[i] = header
            println(buf[i])
            i++
            System.arraycopy(tmp, 0, buf, i, tmp.size)
            i += tmp.size
            println("Data")
            for (b in tmp) {
                print("$b ")
            }
            println("\n")
        }

        // Header first, and then data at the end of the object
        for (element in advancedElements) {
            println("Advanced elements headers:")
            val tmp = getByteList(obj, element)
            val elementType = element.type
            println("Header ($elementType):")
            buf[i] = networkElementToByteMap[elementType]!!
            println(buf[i])
            i++
            println("Length: " + tmp.size)
            buf[i] = tmp.size.toByte()
            i++
            println("\n")
        }

        // Put the data at the end of the object
        for (element in advancedElements) {
            println("Placing data in object:")
            val tmp = getByteList(obj, element)
            val elementType = element.type
            print("Header ($elementType): ")
            println(buf[i])
            println("Length: " + tmp.size)
            System.arraycopy(tmp, 0, buf, i, tmp.size)
            i += tmp.size
            println("Data")
            for (b in tmp) {
                print("$b ")
            }
            println("\n")
        }
        buf[buf.size - 1] = 255.toByte()
        println("Final array:")
        for (b in buf) {
            println(b)
        }
        val rawOut: BufferedOutputStream
        try {
            rawOut = BufferedOutputStream(socket.getOutputStream())
            val out = DataOutputStream(rawOut)
            out.write(buf)
            System.out.flush()
            out.flush()
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    @Throws(MismatchedObject::class)
    fun <T : Any> receiveObject(clazz: Class<T>, socket: Socket): T {
        val rawIn: BufferedInputStream
        val buf = ByteArray(256)
        val len: Int
        try {
            rawIn = BufferedInputStream(socket.getInputStream())
            val `in` = DataInputStream(rawIn)
            len = `in`.read(buf)
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
        println("Received object...")
        for (i in 0 until len) {
            println("Byte: " + buf[i])
        }
        val objectNetworkRepresentation = getObjectStructure(clazz)
        val networkRepresentation = decodeNetworkObject(ByteArrayInputStream(buf))
        if (!objectNetworkRepresentation.equalsStructure(networkRepresentation)) {
            throw MismatchedObject("Objects don't match")
        } else {
            println("Objects match signature")
        }
        return createObject(clazz, ByteArrayInputStream(buf))
    }

    fun getByteList(obj: Any?, element: NetworkElement): ByteArray {
        val type = element.type
        val f = element.field
        return try {
            if (element.isOptional) {
                if (element.field[obj] == null) {
                    return ByteArray(0)
                }
            }
            when (type) {
                NetworkElementType.BYTE -> byteArrayOf(f[obj] as Byte)
                NetworkElementType.SHORT -> ByteBuffer.allocate(2).putShort(f[obj] as Short).array()
                NetworkElementType.INT -> ByteBuffer.allocate(4).putInt(f[obj] as Int).array()
                NetworkElementType.LONG -> ByteBuffer.allocate(8).putLong(f[obj] as Long).array()
                NetworkElementType.UUID -> {
                    val uuid = f[obj] as UUID
                    ByteBuffer.allocate(16).putLong(uuid.mostSignificantBits).putLong(uuid.leastSignificantBits).array()
                }

                NetworkElementType.BOOLEAN -> ByteBuffer.allocate(1).put((if (f[obj] as Boolean) 1 else 0).toByte()).array()
                NetworkElementType.STRING -> (f[obj] as String).toByteArray()
                NetworkElementType.ARRAY -> ByteArray(0)
                null -> TODO()
            }
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    @Throws(ArbiterException::class)
    fun getNetworkRepresentation(clazz: Class<*>): List<NetworkElementType?> {
        val fields = clazz.fields
        val fieldOrder = HashMap<Int, Field>()
        val ret = ArrayList<NetworkElementType?>()
        for (f in fields) {
            if (f.isAnnotationPresent(ZoarialObjectElement::class.java)) {
                val objectElement = f.getAnnotation(ZoarialObjectElement::class.java)
                if (!fieldOrder.containsKey(objectElement.placement)) {
                    fieldOrder[objectElement.placement] = f
                } else {
                    val existingField = fieldOrder[objectElement.placement]
                    val str = "Duplicate placement found: " + objectElement.placement + ". Existing: \"" + existingField + "\". New: \"" + f + "\"."
                    throw DuplicatePlacement(str)
                }
            }
        }

        // Sort the field order by the key (user provided placement)
        val sortedElements = fieldOrder.entries.stream().sorted(Comparator.comparingInt<Map.Entry<Int, Field>> { (key, _) -> key }).collect(Collectors.toList())

        // Print list
        sortedElements.forEach(Consumer { (key, value): Map.Entry<Int, Field> -> println("Entry $key: $value") })
        sortedElements.forEach(Consumer { (_, value): Map.Entry<Int, Field> ->
            val aClass = value.type
            if (classToNetworkElementMap.containsKey(aClass)) {
                val networkType = classToNetworkElementMap[aClass]
                ret.add(networkType)
            } else {
                val str = "Object is not in map: $aClass"
                println(str)
                throw NotANetworkObject(str)
            }
        })
        return ret
    }

    @Throws(ArbiterException::class)
    fun decodeNetworkObject(inputStream: InputStream): List<NetworkElementType?> {
        val `in` = DataInputStream(inputStream)
        val buf = ByteArray(256)
        val read: Int
        val objectLen: Int
        val ret = ArrayList<NetworkElementType?>()
        var advancedElementDataLen = 0
        var readingAdvanced = false
        try {
            read = `in`.read(buf, 0, 3)
            if (read != 3) {
                throw NotANetworkObject("Not ZNA")
            }
            if (Arrays.compare(buf, 0, 3, buf, 0, 3) != 0) {
                throw NotANetworkObject("Not ZNA")
            }
            if (`in`.readByte().toShort() != NETWORK_ARBITER_VERSION) {
                throw NotANetworkObject("Incorrect Arbiter Object Version")
            }
            objectLen = `in`.readUnsignedShort()
            for (i in 0 until objectLen) {
                var rawType = `in`.readByte()
                val optional = rawType.toInt() and (1 shl 7) != 0
                val optionalPresent = optional && rawType.toInt() and (1 shl 6) != 0

                // Remove optional and present flags
                rawType = (rawType.toInt() and 63).toByte()
                if (!byteToNetworkElementMap.containsKey(rawType)) {
                    throw NotANetworkObject("Invalid raw type: $rawType")
                }
                val type = byteToNetworkElementMap[rawType]
                if (isBasicElement(type)) {
                    if (readingAdvanced) {
                        throw NotANetworkObject("Incorrect object structure order from network.")
                    }
                    if (!optional || optionalPresent) {
                        `in`.readNBytes(buf, 0, networkElementLengthMap[type]!!.toInt())
                    }
                    ret.add(type)
                } else {
                    if (!readingAdvanced) {
                        readingAdvanced = true
                    }
                    when (type) {
                        NetworkElementType.STRING -> {
                            // frikin java and their always signed-ness
                            val strLen = (`in`.readByte().toShort().toInt() and 0xFF.toShort().toInt()).toShort()
                            advancedElementDataLen += strLen.toInt()
                            ret.add(type)
                        }

                        NetworkElementType.ARRAY -> throw RuntimeException("Not implemented")
                        else -> throw RuntimeException("Forgot to implement")
                    }
                }
            }
            println("Network Structure: ")
            for (e in ret) {
                println(e)
            }
            `in`.readNBytes(advancedElementDataLen)
            if (`in`.readByte() != 255.toByte()) {
                throw NotANetworkObject("Object doesn't end correctly.")
            }
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
        return ret
    }

    /**
     *
     * @param clazz
     * @param inputStream
     * @return
     * @param <T>
    </T> */
    private fun <T : Any> createObject(clazz: Class<T>, inputStream: InputStream): T {
        if (!clazz.isAnnotationPresent(ZoarialNetworkObject::class.java)) {
            throw NotANetworkObject("Object is not a network object")
        }
        val obj: T = try {
            clazz.getDeclaredConstructor().newInstance()
        } catch (e: InstantiationException) {
            throw RuntimeException(e)
        } catch (e: IllegalAccessException) {
            throw RuntimeException(e)
        } catch (e: InvocationTargetException) {
            throw RuntimeException(e)
        } catch (e: NoSuchMethodException) {
            throw RuntimeException(e)
        } ?: throw RuntimeException("Constructed a null object")

        val objectStructure = getObjectStructure(obj::class.java)
        val basicElements = objectStructure.basicElements
        val advancedElements = objectStructure.advancedElements
        val `in` = DataInputStream(inputStream)
        val buf = ByteArray(256)
        val read: Int
        val objectLen: Int
        val advancedElementLengths = IntArray(advancedElements.size)
        try {
            read = `in`.read(buf, 0, 3)
            if (Arrays.compare(buf, 0, 3, buf, 0, 3) != 0) {
                throw NotANetworkObject("Not ZNA")
            }
            if (`in`.readByte().toShort() != NETWORK_ARBITER_VERSION) {
                throw NotANetworkObject("Incorrect Arbiter Object Version")
            }
            objectLen = `in`.readUnsignedShort()
            println("Object length: $objectLen")
            if (objectLen != basicElements.size + advancedElements.size) {
                throw MismatchedObject("Element length of receiving object does not equal given object")
            }
            for (e in basicElements) {
                var rawType = `in`.readByte()
                val optional = rawType.toInt() and (1 shl 7) != 0
                val optionalPresent = optional && rawType.toInt() and (1 shl 6) != 0
                val present = !optional || optionalPresent

                // Remove optional and present flags
                rawType = (rawType.toInt() and 63).toByte()
                val type = byteToNetworkElementMap[rawType]
                val field = e.field
                if (e.type != type) {
                    throw MismatchedObject("Expected type " + e.type + ". Got: " + type)
                }
                if (!present) {
                    continue
                }
                if (field.type.isPrimitive) {
                    when (type) {
                        NetworkElementType.BYTE -> field.setByte(obj, `in`.readByte())
                        NetworkElementType.SHORT -> field.setShort(obj, `in`.readShort())
                        NetworkElementType.INT -> field.setInt(obj, `in`.readInt())
                        NetworkElementType.LONG -> field.setLong(obj, `in`.readLong())
                        NetworkElementType.BOOLEAN -> field.setBoolean(obj, `in`.readBoolean())
                        else -> throw NotANetworkObject("Unsupported primitive")
                    }
                } else {
                    when (type) {
                        NetworkElementType.BYTE -> field[obj] = `in`.readByte()
                        NetworkElementType.SHORT -> field[obj] = `in`.readShort()
                        NetworkElementType.INT -> field[obj] = `in`.readInt()
                        NetworkElementType.LONG -> field[obj] = `in`.readLong()
                        NetworkElementType.UUID -> field[obj] = UUID(`in`.readLong(), `in`.readLong())
                        NetworkElementType.BOOLEAN -> field[obj] = `in`.readBoolean()
                        else -> throw NotANetworkObject("Unsupported object")
                    }
                }
            }
            for (i in advancedElements.indices) {
                val e = advancedElements[i]
                val rawType = `in`.readByte()
                val type = byteToNetworkElementMap[rawType]
                val field = e.field
                if (field.type.isPrimitive) {
                    throw NotANetworkObject("Unsupported primitive")
                } else {
                    when (type) {
                        NetworkElementType.STRING -> advancedElementLengths[i] = `in`.readByte().toInt() and 0xFF
                        NetworkElementType.ARRAY -> throw RuntimeException("Not implemented")
                        else -> throw NotANetworkObject("Unsupported object")
                    }
                }
            }
            for (i in advancedElements.indices) {
                val e = advancedElements[i]
                val field = e.field
                if (field.type.isPrimitive) {
                    throw NotANetworkObject("Unsupported primitive")
                } else {
                    when (e.type) {
                        NetworkElementType.STRING -> e.field[obj] = String(`in`.readNBytes(advancedElementLengths[i]))
                        NetworkElementType.ARRAY -> throw RuntimeException("Not implemented")
                        else -> throw NotANetworkObject("Unsupported object")
                    }
                }
            }
            if (`in`.readByte() != 255.toByte()) {
                throw NotANetworkObject("Object doesn't end correctly.")
            }
        } catch (e: IOException) {
            throw RuntimeException(e)
        } catch (e: IllegalAccessException) {
            throw RuntimeException(e)
        }
        return obj
    }

    class NetworkElement internal constructor(val index: Int, val type: NetworkElementType?, val field: Field, val isOptional: Boolean)
    class NetworkObject(basicElements: List<NetworkElement>?, advancedElements: List<NetworkElement>?) {
        val basicElements: List<NetworkElement>
        val advancedElements: List<NetworkElement>

        init {
            if (basicElements == null || advancedElements == null) {
                throw RuntimeException("Cannot create with null lists.")
            }
            this.advancedElements = advancedElements
            this.basicElements = basicElements
        }

        fun equalsStructure(other: List<NetworkElementType?>): Boolean {
            if (Objects.isNull(other)) {
                throw RuntimeException("Cannot compare to null object")
            }
            val tmp: MutableList<NetworkElementType?> = ArrayList()
            tmp.addAll(basicElements.stream().map { obj: NetworkElement -> obj.type }.collect(Collectors.toList()))
            tmp.addAll(advancedElements.stream().map { obj: NetworkElement -> obj.type }.collect(Collectors.toList()))
            return tmp == other
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as NetworkObject

            if (basicElements != other.basicElements) return false
            if (advancedElements != other.advancedElements) return false

            return true
        }

        override fun hashCode(): Int {
            var result = basicElements.hashCode()
            result = 31 * result + advancedElements.hashCode()
            return result
        }

    }

    /**
     *
     * @param clazz
     * @return A sorted list of [NetworkElements][NetworkElement]
     */
    fun getObjectStructure(clazz: Class<*>): NetworkObject {
        if (!clazz.isAnnotationPresent(ZoarialNetworkObject::class.java)) {
            throw NotANetworkObject("Object is not a ZoarialNetworkObject.")
        }
        val fields = clazz.fields
        val basicList = ArrayList<NetworkElement>()
        val advancedList = ArrayList<NetworkElement>()
        for (f in fields) {
            if (f.isAnnotationPresent(ZoarialObjectElement::class.java)) {
                val objectElementAnnotation = f.getAnnotation(ZoarialObjectElement::class.java)
                val fieldClass = f.type
                val placement: Int = objectElementAnnotation.placement
                val optional: Boolean = objectElementAnnotation.optional
                if (!classToNetworkElementMap.containsKey(fieldClass)) {
                    if (fieldClass == Optional::class.java) {
                        throw NotANetworkObject("The Optional class is not supported. Please use the `optional` attribute on @ZoarialObjectElement")
                    }
                    val str = "Object is not in map: $fieldClass"
                    println(str)
                    throw NotANetworkObject(str)
                } else if (optional && fieldClass.isPrimitive) {
                    throw RuntimeException("A primitive type cannot be optional: $objectElementAnnotation")
                }
                val networkType = classToNetworkElementMap[fieldClass]
                var correctList: MutableList<NetworkElement> = if (isBasicElement(networkType)) basicList else advancedList
                if (correctList.stream().filter { e: NetworkElement -> e.index == placement }.findFirst().isEmpty) {
                    correctList.add(NetworkElement(placement, networkType, f, optional))
                } else {
                    val networkElement = correctList[placement]
                    throw DuplicatePlacement(networkElement.field.getAnnotation(ZoarialObjectElement::class.java), objectElementAnnotation)
                }
            }
        }

        // Print list
        //sortedElements.forEach(e -> System.out.println("Entry " + e.getIndex() + ": " + e.getType()));
        return NetworkObject(basicList.stream().sorted(Comparator.comparingInt { obj: NetworkElement -> obj.index }).collect(Collectors.toList()), advancedList.stream().sorted(Comparator.comparingInt { obj: NetworkElement -> obj.index }).collect(Collectors.toList()))
    }

    fun isBasicElement(type: NetworkElementType?): Boolean {
        return when (type) {
            NetworkElementType.BYTE, NetworkElementType.SHORT, NetworkElementType.INT, NetworkElementType.LONG, NetworkElementType.BOOLEAN, NetworkElementType.UUID -> true
            NetworkElementType.STRING, NetworkElementType.ARRAY -> false
            null -> TODO()
        }
    }

}