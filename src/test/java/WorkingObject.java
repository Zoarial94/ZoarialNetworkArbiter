import me.zoarial.networkArbiter.annotations.ZoarialObjectElement;
import me.zoarial.networkArbiter.annotations.ZoarialNetworkObject;

import java.util.Objects;
import java.util.UUID;

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

    @ZoarialObjectElement(placement = 12, optional = true)
    public Integer optint1 = null;

    @ZoarialObjectElement(placement = 1)
    public String str1 = "This is a test";

    @ZoarialObjectElement(placement = 11)
    public UUID uuid = UUID.randomUUID();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorkingObject that = (WorkingObject) o;
        return byte1 == that.byte1 && s1 == that.s1 && i1 == that.i1 && l1 == that.l1 && b2 == that.b2 && num1 == that.num1 && b1 == that.b1 && Objects.equals(B1, that.B1) && Objects.equals(str1, that.str1);
    }

    @Override
    public int hashCode() {
        return Objects.hash(byte1, s1, i1, l1, b2, num1, b1, B1, str1);
    }
}