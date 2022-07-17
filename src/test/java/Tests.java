import me.zoarial.networkArbiter.ZoarialNetworkArbiter;
import me.zoarial.networkArbiter.exceptions.ArbiterException;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public class Tests {

    static final AtomicReference<WorkingObject> returnedObject = new AtomicReference<>();
    static final WorkingObject sendingWorkingObject = new WorkingObject();
    public static void main(String[] args) {

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
                returnedObject.wait();
            }
            System.out.println("Done waiting.");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        if(returnedObject.get() != null) {
            System.out.println("Received object is equal to new object: " + returnedObject.get().equals(sendingWorkingObject));
        }

    }

    static class SendingThread implements Runnable {
        @Override
        public void run() {
            System.out.println("Trying to connect to socket...");
            System.out.flush();
            try (Socket socket = new Socket(Inet4Address.getLoopbackAddress(), 9400)) {
                ZoarialNetworkArbiter arbiter = ZoarialNetworkArbiter.getInstance();

                arbiter.sendObject(sendingWorkingObject, socket);
                try {
                    //arbiter.sendObject(new NotWorkingObject());
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
            System.out.println("Trying to start server...");
            System.out.flush();
            try(ServerSocket serverSocket = new ServerSocket(9400)) {

                ZoarialNetworkArbiter arbiter = ZoarialNetworkArbiter.getInstance();

                Optional<WorkingObject> workingObjectOptional = arbiter.receiveObject(WorkingObject.class, serverSocket.accept());

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
