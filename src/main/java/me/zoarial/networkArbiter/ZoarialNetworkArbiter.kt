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
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.stream.Collectors

object ZoarialNetworkArbiter {

    private val networkObjectStructureCache = HashMap<String, NetworkObjectStructure>()

    private const val NOT_ZNA_ERR_STR: String = "Not ZNA"
    private const val UNSUPPORTED_PRIMITIVE_ERR_STR: String = "Unsupported primitive"
    private const val UNSUPPORTED_OBJECT_ERR_STR: String = "Unsupported object"

    private const val NETWORK_ARBITER_VERSION: Byte = 1
    private val IDENTIFIER_BYTES = "ZNA".toByteArray(StandardCharsets.US_ASCII)

    /**
     *  @param obj The entire object is needed in case there are advanced elements. Advanced elements (such as lists) need to know the run-time value
     *  @param socket The socket used to send the data
     */

    @Throws(ArbiterException::class)
    fun sendObject(obj: Any, socket: Socket) {
        if (Objects.isNull(obj)) {
            throw NotANetworkObject("Unable to send a null object")
        }
        val c: Class<*> = obj.javaClass
        if (!c.isAnnotationPresent(ZoarialNetworkObject::class.java)) {
            throw NotANetworkObject("The class " + c.simpleName + " is not a " + ZoarialNetworkObject::class.java.simpleName)
        }
        println("\n")
        val networkObject = NetworkObject(obj)
        println("The total length for this object is: ${networkObject.getLength()}")
        val basicElements = networkObject.structure.basicElements
        val advancedElements = networkObject.structure.advancedElements

        // 3 for ZNA, 1 for Network Version, 2 for length, 1 for 255 as the last byte
        // Structure should be as follows: Header, Basic element headers, advanced element headers, basic elements, advanced elements
        var i = 6
        val buf = ByteArray(networkObject.getLength())
        System.arraycopy(IDENTIFIER_BYTES, 0, buf, 0, 3)
        buf[3] = NETWORK_ARBITER_VERSION
        System.arraycopy(ByteBuffer.allocate(2).putShort((basicElements.size + advancedElements.size).toShort()).array(), 0, buf, 4, 2)
        println("Working loop: \n")

        println("Basic Headers:")
        // Place headers in buffer
        for (element in basicElements) {
            //TODO: Maybe check to see if value is not null
            val header = element.type.getID()
            println("Header ID: ${header.toUByte()} ${element.type}")
            buf[i] = header
            i++
        }
        println()

        // Header first, and then data at the end of the object
        for (element in advancedElements) {
            println("Advanced element headers:")
            val tmp = getByteList(element)
            val elementType = element.type
            println("Header ID: ${elementType.getID().toUByte()} $elementType\nArray: ${element.isArray}")
            if(element.isArray) {
                buf[i] = NetworkElementType.ARRAY.getID()
                println(buf[i])
                i++
            }
            buf[i] = elementType.getID()
            i++
            println("Length: " + tmp.size)
            buf[i] = tmp.size.toByte()
            i++
            println("\n")
        }

        // Put the data at the end of the object
        for(element in basicElements.plus(advancedElements)) {
            val tmp: ByteArray = getByteList(element)
            System.arraycopy(tmp, 0, buf, i, tmp.size)
            i += tmp.size
            print("Data: ")
            for (b in tmp) {
                print("${b.toUByte()} ")
            }
            println()

        }
        buf[buf.size - 1] = 255.toByte()
        println("Final array:")
        for (b in buf) {
            println(b.toUByte())
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
        val networkObjectStructureOpt = getNetworkObjectStructure(clazz)
        if(networkObjectStructureOpt.isEmpty) {
            throw RuntimeException("Object is not registered")
        }

        val buf = ByteArray(256)
        val numberOfElements: Int
        val rawIn = BufferedInputStream(socket.getInputStream())
        val inputStream = DataInputStream(rawIn)

        // Initial Error Checking
        try {
            // Make sure this is a ZNA Object
            require(
                inputStream.readNBytes(3)
                    .zip(IDENTIFIER_BYTES) { b: Byte, c: Byte -> b == c }.none(Boolean::not)
            ) {
                throw RuntimeException("Not ZNA Header")
            }

            // Make sure the version is supported
            require(inputStream.readByte() == NETWORK_ARBITER_VERSION) {
                throw RuntimeException("Unsupported ZNA Version")
            }
            numberOfElements = inputStream.readShort().toInt()
            println("Receiving object...")
            println("Number of elements: $numberOfElements")
        } catch (e: IOException) {
            throw RuntimeException(e)
        }

        // TODO: Pass in the right object
        val networkRepresentation = decodeNetworkObject(inputStream, numberOfElements)
        if (!networkObjectStructureOpt.get().equalsStructure(networkRepresentation)) {
            throw MismatchedObject("Objects don't match")
        } else {
            println("Objects match signature")
        }
        //TODO: More major refactoring
        // This function should take in the info generated from decodeNetworkObject and create an object from the rest of the stream
        // This means that decodeNetworkObject need to return more data.
        return createObject(clazz, ByteArrayInputStream(buf))
    }

    private fun boolToByte(boolean: Boolean): Byte {
        return if(boolean) {
            1.toByte()
        } else {
            0.toByte()
        }
    }

    /**
     * Take in any [NetworkElementType] object and return the same object as an array of bytes
     */
    private fun getByteList(element: NetworkElement): ByteArray {
        val obj = element.obj
        val type = element.type
        val f = element.field
        return try {
            // Return an empty array if the optional is empty
            if (f[obj] == null) {
                TODO() // What should I do here?
            }

            // TODO: Remove isArray edge case (Probably after/during implementation of sub-objects)
            if(!element.isArray) { // Handle single elements
                when (type) {
                    NetworkElementType.BYTE -> byteArrayOf(f[obj] as Byte)
                    NetworkElementType.SHORT -> ByteBuffer.allocate(2).putShort(f[obj] as Short).array()
                    NetworkElementType.INT -> ByteBuffer.allocate(4).putInt(f[obj] as Int).array()
                    NetworkElementType.LONG -> ByteBuffer.allocate(8).putLong(f[obj] as Long).array()
                    NetworkElementType.UUID -> {
                        val uuid = f[obj] as UUID
                        ByteBuffer.allocate(16).putLong(uuid.mostSignificantBits).putLong(uuid.leastSignificantBits)
                            .array()
                    }

                    NetworkElementType.BOOLEAN -> ByteBuffer.allocate(1).put(boolToByte(f[obj] as Boolean)).array()

                    NetworkElementType.STRING -> (f[obj] as String).toByteArray()
                    NetworkElementType.ARRAY -> throw IllegalStateException()
                    NetworkElementType.SUB_OBJECT -> TODO()
                }
            } else { // Handle arrays of elements
                val list = f[obj] as List<*>
                val elementSize = type.getLength()
                val buffer = ByteBuffer.allocate(list.size * elementSize)
                @Suppress("UNCHECKED_CAST")
                when (type) {
                    NetworkElementType.BYTE -> (list as List<Byte>).listIterator().forEach { buffer.put(it) }
                    NetworkElementType.SHORT -> (list as List<Short>).listIterator()
                        .forEach { buffer.putShort(it) }

                    NetworkElementType.INT -> (list as List<Int>).listIterator().forEach { buffer.putInt(it) }
                    NetworkElementType.LONG -> (list as List<Long>).listIterator().forEach { buffer.putLong(it) }
                    NetworkElementType.BOOLEAN -> (list as List<Boolean>).listIterator().forEach { buffer.put(boolToByte(it)) }
                    NetworkElementType.STRING -> throw RuntimeException("Type not supported in arrays")
                    NetworkElementType.UUID -> {
                        (list as List<UUID>).listIterator().forEach { buffer.putLong(it.mostSignificantBits).putLong(it.leastSignificantBits) }
                    }
                    NetworkElementType.ARRAY -> throw IllegalStateException("Multi-dimensional arrays are not supported") // There are no arrays of arrays (not yet at least)
                    NetworkElementType.SUB_OBJECT -> TODO()
                }
                buffer.array()
            }
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    /**
     * This function will be called after the program has already confirmed this is a ZNA object.
     * @param inputStream should be at least the rest of the object.
     */
    @Throws(ArbiterException::class)
    fun decodeNetworkObject(inputStream: InputStream, numberOfElements: Int): List<NetworkElementType> {
        val inputDataStream = DataInputStream(inputStream)
        val ret = ArrayList<NetworkElementType>()
        var readingAdvanced = false
        try {
            for (i in 0 until numberOfElements) {
                // Get the type and throw otherwise
                val type = NetworkElementType.getTypeOrThrow(inputDataStream.readByte())

                if (type.isAdvancedElement() && !readingAdvanced) {
                    readingAdvanced = true
                } else if (type.isBasicElement() && readingAdvanced) {
                        throw NotANetworkObject("Incorrect object structure order from network.")
                }

                if(type == NetworkElementType.ARRAY) {
                    ret.add(NetworkElementType.getTypeOrThrow(inputDataStream.readByte()))
                } else {
                    ret.add(type)
                }

                when(type) {
                    NetworkElementType.ARRAY, NetworkElementType.STRING -> {
                        // Cleanup extra bytes
                        inputDataStream.readByte()
                    }
                    NetworkElementType.SUB_OBJECT -> TODO()

                    // Do nothing with basic types
                    NetworkElementType.BYTE, NetworkElementType.SHORT, NetworkElementType.INT,
                    NetworkElementType.LONG, NetworkElementType.BOOLEAN, NetworkElementType.UUID -> continue
                }
            }
            println("Network Structure: ")
            ret.forEachIndexed {index, element ->
                print("$element ")
                if(index != 0 && index % 8 == 0) {
                    println()
                }
            }
            println()
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

        val objectStructure = getNetworkObjectStructure(obj)
        val basicElements = objectStructure.basicElements
        val advancedElements = objectStructure.advancedElements
        val inputDataStream = DataInputStream(inputStream)
        val buf = ByteArray(256)
        val read: Int
        val objectLen: Int
        val advancedElementLengths = IntArray(advancedElements.size)
        try {
            read = inputDataStream.read(buf, 0, 3)
            if (read < 3) {
                throw NotANetworkObject("Not enough buffered data")
            }
            if (Arrays.compare(buf, 0, 3, buf, 0, 3) != 0) {
                throw NotANetworkObject(NOT_ZNA_ERR_STR)
            }
            if (inputDataStream.readByte() != NETWORK_ARBITER_VERSION) {
                throw NotANetworkObject("Incorrect Arbiter Object Version")
            }
            objectLen = inputDataStream.readUnsignedShort()
            println("Object length: $objectLen")
            if (objectLen != basicElements.size + advancedElements.size) {
                throw MismatchedObject("Element length of receiving object does not equal given object")
            }
            for (e in basicElements) {
                var rawType = inputDataStream.readByte()
                val optional = rawType.toInt() and (1 shl 7) != 0
                val optionalPresent = optional && rawType.toInt() and (1 shl 6) != 0
                val present = !optional || optionalPresent

                // Remove optional and present flags
                rawType = (rawType.toInt() and 63).toByte()
                val type = NetworkElementType.getTypeOrThrow(rawType)
                val field = e.field
                if (e.type != type) {
                    throw MismatchedObject("Expected type " + e.type + ". Got: " + type)
                }
                if (!present) {
                    continue
                }
                if (field.type.isPrimitive) {
                    when (type) {
                        NetworkElementType.BYTE -> field.setByte(obj, inputDataStream.readByte())
                        NetworkElementType.SHORT -> field.setShort(obj, inputDataStream.readShort())
                        NetworkElementType.INT -> field.setInt(obj, inputDataStream.readInt())
                        NetworkElementType.LONG -> field.setLong(obj, inputDataStream.readLong())
                        NetworkElementType.BOOLEAN -> field.setBoolean(obj, inputDataStream.readBoolean())
                        else -> throw NotANetworkObject(UNSUPPORTED_PRIMITIVE_ERR_STR)
                    }
                } else {
                    when (type) {
                        NetworkElementType.BYTE -> field[obj] = inputDataStream.readByte()
                        NetworkElementType.SHORT -> field[obj] = inputDataStream.readShort()
                        NetworkElementType.INT -> field[obj] = inputDataStream.readInt()
                        NetworkElementType.LONG -> field[obj] = inputDataStream.readLong()
                        NetworkElementType.UUID -> field[obj] = UUID(inputDataStream.readLong(), inputDataStream.readLong())
                        NetworkElementType.BOOLEAN -> field[obj] = inputDataStream.readBoolean()
                        else -> throw NotANetworkObject(UNSUPPORTED_OBJECT_ERR_STR)
                    }
                }
            }
            for (i in advancedElements.indices) {
                val e = advancedElements[i]
                val type = NetworkElementType.getTypeOrThrow(inputDataStream.readByte())
                val field = e.field
                if (field.type.isPrimitive) {
                    throw NotANetworkObject(UNSUPPORTED_PRIMITIVE_ERR_STR)
                } else {
                    when (type) {
                        NetworkElementType.STRING -> advancedElementLengths[i] = inputDataStream.readByte().toInt() and 0xFF
                        else -> throw NotANetworkObject(UNSUPPORTED_OBJECT_ERR_STR)
                    }
                }
            }
            for (i in advancedElements.indices) {
                val e = advancedElements[i]
                val field = e.field
                if (field.type.isPrimitive) {
                    throw NotANetworkObject(UNSUPPORTED_PRIMITIVE_ERR_STR)
                } else {
                    when (e.type) {
                        NetworkElementType.STRING -> e.field[obj] = String(inputDataStream.readNBytes(advancedElementLengths[i]))
                        else -> throw NotANetworkObject(UNSUPPORTED_OBJECT_ERR_STR)
                    }
                }
            }
            if (inputDataStream.readByte() != 255.toByte()) {
                throw NotANetworkObject("Object doesn't end correctly.")
            }
        } catch (e: IOException) {
            throw RuntimeException(e)
        } catch (e: IllegalAccessException) {
            throw RuntimeException(e)
        }
        return obj
    }

    class NetworkElement internal constructor(val obj: Any, val index: Int, val type: NetworkElementType, val field: Field, val isArray: Boolean, var byteLength: Int = 0)

    class NetworkObjectStructure(val basicElements: List<NetworkElement>, val advancedElements: List<NetworkElement>) {

        fun equalsStructure(other: List<NetworkElementType>): Boolean {
            //TODO: How do I better handle arrays without losing information?
            // Currently arrays are passed through as their type. Array of Ints is passed as an Int
            val combinedList = basicElements.plus(advancedElements)

            if(combinedList.size != other.size) {
                return false;
            }

            return other.zip(basicElements.plus(advancedElements)).all { a -> a.first == a.second.type }
        }

        fun isAdvancedObject(): Boolean {
            return advancedElements.isNotEmpty()
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as NetworkObjectStructure

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

    class NetworkObject(val structure: NetworkObjectStructure, val obj: Any) {
        constructor(obj: Any) : this(getNetworkObjectStructure(obj), obj)

        private var cachedDataHashCode: Int = obj.hashCode()
        var objBasicLength: Int = 0
        var objAdvLength: Int = 0

        init {
            calcBasicLength()
            calcAdvLength()
        }

        fun isAdvancedObject(): Boolean {
            return structure.isAdvancedObject()
        }

        /** @return is always true if the object is only a basic object
         */
        private fun isCacheValid(): Boolean {
            return isAdvancedObject().not().or(cachedDataHashCode == obj.hashCode())
        }

        fun getLength(): Int {
            if(!isCacheValid()) {
                calcObjectLength()
            }
            // 7 is the object header information
            return 7 + objBasicLength + objAdvLength
        }
        private fun calcObjectLength() {
            //TODO: Should this really be synchronized?
            synchronized(obj) {
                if (!isCacheValid()) {
                    calcAdvLength()
                }
                cachedDataHashCode = obj.hashCode()
            }
        }
        private fun calcBasicLength() {
            objBasicLength = structure.basicElements.parallelStream().map { e: NetworkElement ->
                // Add 1 for the element header
                1 + e.type.getLength()
            }.reduce {
                length1: Int, length2: Int -> length1 + length2
            }.get()
        }

        private fun calcAdvLength() {
            objAdvLength = structure.advancedElements.parallelStream().map { e: NetworkElement ->
                val type = e.type
                if (type.isBasicElement() && !e.isArray) {
                    throw RuntimeException("Only advanced elements should be here. Got: $type")
                }
                try {
                    if (e.isArray) {
                        return@map type.getLength() * (e.field[obj] as List<*>).size + 3
                    } else {
                        when (type) {
                            NetworkElementType.STRING -> return@map (e.field[obj] as String).length + 2
                            else -> TODO()
                        }
                    }
                } catch (ex: IllegalAccessException) {
                    throw RuntimeException("Something went wrong when getting length for advanced elements.")
                }
            }.reduce { length1: Int, length2: Int -> length1 + length2 }.get()
        }
    }

    private fun getNetworkObjectStructure(clazz: Class<*>): Optional<NetworkObjectStructure> {
        return Optional.ofNullable(networkObjectStructureCache[clazz.canonicalName])
    }

    fun registerNetworkObjectStructure(obj: Any) {
        val clazz = obj.javaClass
        if (!clazz.isAnnotationPresent(ZoarialNetworkObject::class.java)) {
            throw NotANetworkObject("Object is not a ZoarialNetworkObject.")
        }
        val fields = clazz.fields
        val basicList = ArrayList<NetworkElement>()
        val advancedList = ArrayList<NetworkElement>()
        for (f in fields) {
            if (!f.isAnnotationPresent(ZoarialObjectElement::class.java)) { // Skip if not a part of the ZNA object
                continue
            }

            val type = NetworkElementType.getTypeOrThrow(f.type, "Object Not Supported: ${f.type}")
            when {
                type == NetworkElementType.ARRAY -> handleListNetworkElement(obj, f, advancedList)
                type.isBasicElement() -> handleNetworkObject(obj, f, basicList)
                type.isAdvancedElement() -> handleNetworkObject(obj, f, advancedList)
                else -> throw RuntimeException("This should never be reached")
            }
        }

        // Sort the lists and create the new NetworkObject
        val ret = NetworkObjectStructure(
            basicList.stream().sorted(Comparator.comparingInt { obj1: NetworkElement -> obj1.index }).collect(Collectors.toList()),
            advancedList.stream().sorted(Comparator.comparingInt { obj1: NetworkElement -> obj1.index }).collect(Collectors.toList())
        )
        networkObjectStructureCache[clazz.canonicalName] = ret
    }


    private fun handleNetworkObject(
        obj: Any,
        f: Field,
        basicList: ArrayList<NetworkElement>
    ) {

        val objectElementAnnotation = f.getAnnotation(ZoarialObjectElement::class.java)
        val fieldClass = f.type
        val placement: Int = objectElementAnnotation.placement
        val networkType = NetworkElementType.getType(f.type)

        // Error Checking
        if(networkType.isEmpty) {
            throw NotANetworkObject("Object is not in map: $fieldClass")
        }

        // Get Type
        // TODO: Optimization: Make this error checking happen after the lists have been put together
        if (basicList.stream().filter { e: NetworkElement -> e.index == placement }.findFirst().isEmpty) {
            basicList.add(NetworkElement(obj, placement, networkType.get(), f, false))
        } else {
            throw DuplicatePlacement(
                basicList[placement].field.getAnnotation(ZoarialObjectElement::class.java),
                objectElementAnnotation
            )
        }
    }
    private fun handleListNetworkElement(
        obj: Any,
        f: Field,
        advancedList: ArrayList<NetworkElement>
    ) {
        val list = f[obj] as List<*>
        val objectElementAnnotation = f.getAnnotation(ZoarialObjectElement::class.java)
        val placement: Int = objectElementAnnotation.placement
        when {
            list.isEmpty() -> throw RuntimeException("List is empty")
            list.stream().filter { item -> item == null }.count() != 0L -> throw RuntimeException("List contains a null object")
        }

        val clazz = list[0]!!.javaClass
        val networkType = NetworkElementType.getTypeOrThrow(clazz)
        advancedList.add(NetworkElement(obj, placement, networkType, f, isArray = true))
    }

    /**
     *
     * Return the object's network structure and cache the value for future use
     * @param obj is an object which has the [ZoarialNetworkObject] annotation
     * @return A sorted list of [NetworkElements][NetworkElement]
     */
    private fun getNetworkObjectStructure(obj: Any): NetworkObjectStructure {

        val clazz = obj.javaClass
        val objectIsCached = networkObjectStructureCache.containsKey(clazz.canonicalName)
        if(objectIsCached) {
            return getNetworkObjectStructure(clazz).get()
        }

        System.err.println("Class was not already registered: " + clazz.canonicalName)
        registerNetworkObjectStructure(obj)
        return getNetworkObjectStructure(clazz).orElseThrow { NoSuchElementException("Failed to create object structure from object") }
    }


}