import me.zoarial.NetworkArbiter.annotations.ZoarialObjectElement;
import me.zoarial.NetworkArbiter.annotations.ZoarialNetworkObject;

@ZoarialNetworkObject
public class NotWorkingObject {

    @ZoarialObjectElement(placement = 1)
    public boolean b2 = true;

    @ZoarialObjectElement(placement = 2)
    public int num1 = 1;

    @ZoarialObjectElement(placement = 3)
    public boolean b1 = true;

    @ZoarialObjectElement(placement = 3)
    public boolean b3 = true;
}
