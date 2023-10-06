import me.zoarial.networkArbiter.ZoarialNetworkArbiter;
import me.zoarial.networkArbiter.exceptions.ArbiterException;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Tests {

    static final AtomicReference<WorkingObject> returnedObject = new AtomicReference<>();
    static final WorkingObject sendingWorkingObject = new WorkingObject();

    @Test
    void WorkingObjectTest() {

        sendingWorkingObject.l1 = 27558;
        sendingWorkingObject.s1 = 12555;
        sendingWorkingObject.str1 = "Another test string";

        Thread receivingThread = new Thread(new ReceivingThread());
        Thread sendingThread = new Thread(new SendingThread());

        receivingThread.start();
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        sendingThread.start();

        try {
            System.out.println("Waiting...");
            synchronized (returnedObject) {
                returnedObject.wait(2000);
            }
            System.out.println("Done waiting.");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        assertEquals(sendingWorkingObject, returnedObject.get());

    }

    static class SendingThread implements Runnable {
        @Override
        public void run() {
            System.out.println("Trying to connect to socket...");
            System.out.flush();
            try (Socket socket = new Socket(Inet4Address.getLoopbackAddress(), 9400)) {
                ZoarialNetworkArbiter arbiter = ZoarialNetworkArbiter.INSTANCE;

                arbiter.sendObject(sendingWorkingObject, socket);
                try {
                    //arbiter.sendObject(new NotWorkingObject());
                } catch(ArbiterException ignored) {

                }

            } catch (IOException e) {
                returnedObject.notify();
                throw new RuntimeException(e);
            }
        }
    }

    static class ReceivingThread implements Runnable {
        @Override
        public void run() {
            System.out.println("Trying to start server...");
            System.out.flush();
            try(ServerSocket serverSocket = new ServerSocket(9400)) {

                ZoarialNetworkArbiter arbiter = ZoarialNetworkArbiter.INSTANCE;

                WorkingObject workingObject = arbiter.receiveObject(WorkingObject.class, serverSocket.accept());

                returnedObject.set(workingObject);
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
