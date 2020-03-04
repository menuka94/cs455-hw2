package cs455.scaling.task;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import cs455.scaling.util.Constants;
import cs455.scaling.util.HashUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ReadAndRespond implements Task {
    private static final Logger log = LogManager.getLogger(ReadAndRespond.class);
    private SelectionKey key;

    public ReadAndRespond(SelectionKey key) {
        this.key = key;
    }

    @Override
    public void execute() throws IOException {
        log.debug("ReadAndRespond.execute()");
        // Create a buffer to read into
        ByteBuffer buffer = ByteBuffer.allocate(Constants.MESSAGE_SIZE);

        // Grab the socket from the key
        SocketChannel clientSocket = (SocketChannel) key.channel();

        // Read from it
        int bytesRead = clientSocket.read(buffer);

        // Handle a closed connection
        if (bytesRead == -1) {
             clientSocket.close();
            // log.info("\t\tClient disconnected.");
            log.warn("bytesRead is -1");
        } else {
            // Return the hash of message back to the client
            // log.info("\t\tReceived: " + new String(buffer.array()));

            String digest = HashUtil.SHA1FromBytes(buffer.array());

            buffer.clear();
            buffer.put(digest.getBytes());

            // Flip the buffer now write
            buffer.flip();
            clientSocket.write(buffer);

            // Clear the buffer
            buffer.clear();
        }
        key.attach(null);
    }
}
