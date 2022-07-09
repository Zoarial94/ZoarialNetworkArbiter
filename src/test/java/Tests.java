import me.zoarial.NetworkArbiter.ZoarialNetworkArbiter;

import java.net.Socket;

public class Tests {
    public static void main(String[] args) {
        ZoarialNetworkArbiter arbiter = new ZoarialNetworkArbiter(new Socket());
        arbiter.sendObject(new WorkingObject());
        arbiter.sendObject(new NotWorkingObject());
    }
}
