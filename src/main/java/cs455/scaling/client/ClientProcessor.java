package cs455.scaling.client;

import cs455.scaling.util.Constants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

public class ClientProcessor extends Thread {
    private static final Logger log = LogManager.getLogger(ClientProcessor.class);

    public AtomicLong noOfMessagesReceived;
    private SocketChannel socketChannel;
    private LinkedBlockingQueue<String> messagesSent;

    public ClientProcessor(SocketChannel socketChannel, LinkedBlockingQueue<String> messagesSent) {
        this.socketChannel = socketChannel;
        this.messagesSent = messagesSent;
    }

    @Override
    public void run() {
        noOfMessagesReceived = new AtomicLong(0);
        log.info("Starting ClientProcessor");

        while (true) {
            try {
                ByteBuffer byteBuffer = ByteBuffer.allocate(Constants.SHA1_DIGEST_SIZE);
                socketChannel.read(byteBuffer);
                String response = new String(byteBuffer.array());

                log.info("Response: " + response);
                if (messagesSent.contains(response)) {
                    messagesSent.remove(response);
                    log.info("Hash matched. Removing ...");
                } else {
                    log.warn("Hash not found in sent messages");
                }
                noOfMessagesReceived.getAndIncrement();
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                log.error("Error in ClientProcessor");
                e.printStackTrace();
            }
        }
    }
}