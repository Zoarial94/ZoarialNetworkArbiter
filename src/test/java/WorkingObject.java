import me.zoarial.NetworkArbiter.annotations.ZoarialObjectElement;
import me.zoarial.NetworkArbiter.annotations.ZoarialNetworkObject;

@ZoarialNetworkObject
public class WorkingObject {

    @ZoarialObjectElement(placement = 20)
    public byte byte1 = 5;
    @ZoarialObjectElement(placement = 21)
    public short s1 = 8;
    @ZoarialObjectElement(placement = 22)
    public int i1 = 44;
    @ZoarialObjectElement(placement = 23)
    public long l1 = 555;

    @ZoarialObjectElement(placement = 24)
    public boolean b2 = true;

    @ZoarialObjectElement(placement = 2)
    public int num1 = 1;

    @ZoarialObjectElement(placement = 5)
    public boolean b1 = true;

    @ZoarialObjectElement(placement = 10)
    public Boolean B1 = false;

}