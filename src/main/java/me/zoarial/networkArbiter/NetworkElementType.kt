package me.zoarial.networkArbiter

import java.util.*

enum class NetworkElementType {
    BYTE {
        override fun getLength(): Byte {
            return 1
        }
    }, SHORT {
        override fun getLength(): Byte {
            return 2
        }
    }, INT {
        override fun getLength(): Byte {
            return 4
        }
    }, LONG {
        override fun getLength(): Byte {
            return 8
        }
    }, BOOLEAN {
        override fun getLength(): Byte {
            return 1
        }
    }, UUID {
        override fun getLength(): Byte {
            return 16
        }
    }, STRING {
        override fun getLength(): Byte {
            throw IllegalStateException(LENGTH_RUNTIME_ERROR_MESSAGE)
        }
    }, ARRAY {
        override fun getLength(): Byte {
            throw IllegalStateException(LENGTH_RUNTIME_ERROR_MESSAGE)
        }
    }, SUB_OBJECT {
        override fun getLength(): Byte {
            throw IllegalStateException(LENGTH_RUNTIME_ERROR_MESSAGE)
        }
    };

    fun getID(): Byte {
        return (ordinal + 1).toByte()
    }

    abstract fun getLength(): Byte

    companion object {
        private val classToNetworkElementMap = HashMap<Class<*>, NetworkElementType>()
        private val byteToNetworkElementMap = HashMap<Byte, NetworkElementType>()
        private const val LENGTH_RUNTIME_ERROR_MESSAGE = "Length only known at run-time"

        init {
            classToNetworkElementMap[String::class.javaObjectType] = STRING
            classToNetworkElementMap[java.util.UUID::class.javaObjectType] = UUID
            classToNetworkElementMap[List::class.java] = ARRAY
            // Add the Java boxed types
            classToNetworkElementMap[Byte::class.javaObjectType] = BYTE
            classToNetworkElementMap[Short::class.javaObjectType] = SHORT
            classToNetworkElementMap[Int::class.javaObjectType] = INT
            classToNetworkElementMap[Long::class.javaObjectType] = LONG
            classToNetworkElementMap[Boolean::class.javaObjectType] = BOOLEAN
            // Add the Java primitive types
            classToNetworkElementMap[Byte::class.javaPrimitiveType!!] = classToNetworkElementMap[Byte::class.javaObjectType]!!
            classToNetworkElementMap[Short::class.javaPrimitiveType!!] = classToNetworkElementMap[Short::class.javaObjectType]!!
            classToNetworkElementMap[Int::class.javaPrimitiveType!!] = classToNetworkElementMap[Int::class.javaObjectType]!!
            classToNetworkElementMap[Long::class.javaPrimitiveType!!] = classToNetworkElementMap[Long::class.javaObjectType]!!
            classToNetworkElementMap[Boolean::class.javaPrimitiveType!!] = classToNetworkElementMap[Boolean::class.javaObjectType]!!

            for(e in NetworkElementType.values()) {
                byteToNetworkElementMap[e.getID()] = e
            }
        }
        fun isBasicElement(type: NetworkElementType): Boolean {
            return when (type) {
                BYTE, SHORT, INT, LONG, BOOLEAN, UUID -> true
                STRING,ARRAY,SUB_OBJECT -> false
            }
        }

        fun getType(obj: Any) : Optional<NetworkElementType> {
            return getType(obj.javaClass)
        }

        fun getType(b: Byte) : Optional<NetworkElementType> {
            return Optional.ofNullable(byteToNetworkElementMap[b])
        }

        fun getType(list: List<Any>) : Optional<NetworkElementType> {
            when {
                list.isEmpty() -> return Optional.empty()
            }
            return getType(list[0].javaClass)
        }
        fun getType(type: Class<*>) : Optional<NetworkElementType> {
            return Optional.ofNullable(classToNetworkElementMap[type])
        }

        fun getTypeOrThrow(type: Class<*>): NetworkElementType {
            return getTypeOrThrow(type, "Not an element: $type")
        }
        fun getTypeOrThrow(type: Class<*>, s: String): NetworkElementType {
            return getType(type).orElseThrow{ RuntimeException(s) }
        }

        fun getTypeOrThrow(type: Byte): NetworkElementType {
            return getTypeOrThrow(type, "Not an element: $type")
        }

        fun getTypeOrThrow(type: Byte, s: String): NetworkElementType {
            return getType(type).orElseThrow{ RuntimeException(s) }
        }


    }

    fun isBasicElement() : Boolean {
        return Companion.isBasicElement(this)
    }

    fun isAdvancedElement() : Boolean {
        return !isBasicElement()
    }

}