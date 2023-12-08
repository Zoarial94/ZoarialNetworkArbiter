package me.zoarial.networkArbiter.exceptions

import me.zoarial.networkArbiter.annotations.ZoarialObjectElement

class NotANetworkObject(str: String?) : ArbiterException(str) {
    @ZoarialObjectElement(placement = 12)
    var optint1: Int? = null
}