import me.zoarial.NetworkArbiter.ZoarialNetworkArbiter;
import me.zoarial.NetworkArbiter.exceptions.ArbiterException;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public class Tests {

    static AtomicReference<WorkingObject> returnedObject = new AtomicReference<>();
    public static void main(String[] args) {
        Thread sendingThread = new Thread(new SendingThread());
        Thread receivingThread = new Thread(new ReceivingThread());

        sendingThread.start();
        receivingThread.start();

        try {
            System.out.println("Waiting...");
            synchronized (returnedObject) {
                returnedObject.wait();
            }
            System.out.println("Done waiting.");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        if(returnedObject.get() != null) {
            System.out.println("Received object is equal to new object: " + returnedObject.get().equals(new WorkingObject()));
        }

    }

    static class SendingThread implements Runnable {
        @Override
        public void run() {
            try {
                ZoarialNetworkArbiter arbiter = new ZoarialNetworkArbiter(new Socket(Inet4Address.getLoopbackAddress(), 9400));

                arbiter.sendObject(new WorkingObject());
                try {
                    arbiter.sendObject(new NotWorkingObject());
                } catch(ArbiterException ignored) {

                }

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    static class ReceivingThread implements Runnable {
        @Override
        public void run() {
            try(ServerSocket serverSocket = new ServerSocket(9400)) {

                ZoarialNetworkArbiter arbiter = new ZoarialNetworkArbiter(serverSocket.accept());

                Optional<WorkingObject> workingObjectOptional = arbiter.receiveObject(WorkingObject.class);

                returnedObject.set(workingObjectOptional.get());
                synchronized (returnedObject) {
                    returnedObject.notify();
                }

            } catch (IOException e) {
                returnedObject.notify();
                throw new RuntimeException(e);
            }
        }
    }

}
