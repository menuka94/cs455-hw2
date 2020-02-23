package cs455.scaling.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Server {
    private static final Logger LOGGER = LogManager.getLogger(Server.class);

    public static void main(String[] args) throws IOException {
        // Open the selector
        Selector selector = Selector.open();

        // Create input channel
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress("localhost", 5600));

        // Register channel to the selector
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        // Loop on selector
        while (true) {
            LOGGER.info("Listening for new connections or messages");

            // Block here
            selector.select();
            LOGGER.info("\tActivity on selector!");

            // Keys are ready
            Set<SelectionKey> selectedKeys = selector.selectedKeys();

            Iterator<SelectionKey> iterator = selectedKeys.iterator();
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                if (!key.isValid()) {
                    continue;
                }

                // New connection on serverSocketChannel
                if (key.isAcceptable()) {
                    register(selector, serverSocketChannel);
                }

                // Previous connection has data to read
                if (key.isReadable()) {
                    readAndRespond(key);
                }

                // Remove it from our set
                iterator.remove();
            }
        }
    }

    private static void register(Selector selector, ServerSocketChannel serverSocketChannel)
            throws IOException {
        // Grab the incoming socket from the serverSocketChannel
        SocketChannel client = serverSocketChannel.accept();
        // Configure it to be a new channel and key that our selector should monitor
        client.configureBlocking(false);
        client.register(selector, SelectionKey.OP_READ);
        LOGGER.info("\t\tNew Client Registered");
    }

    private static void readAndRespond(SelectionKey key) throws IOException {
        // Create a buffer to read into
        ByteBuffer buffer = ByteBuffer.allocate(256);

        // Grab the socket from the key
        SocketChannel client = (SocketChannel) key.channel();

        // Read from it
        int bytesRead = client.read(buffer);

        // Handle a closed connection
        if (bytesRead == -1) {
            client.close();
            LOGGER.info("\t\tClient disconnected.");
        } else {
            // Return their message to them
            LOGGER.info("\t\tReceived: " + new String(buffer.array()));

            // Flip the buffer now write
            buffer.flip();
            client.write(buffer);

            // Clear the buffer
            buffer.clear();
        }
    }
}
