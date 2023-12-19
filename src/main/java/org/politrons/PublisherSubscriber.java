package org.politrons;

import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.driver.MediaDriver;
import io.aeron.logbuffer.FragmentHandler;
import io.aeron.samples.SamplesUtil;
import org.agrona.BufferUtil;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.SigInt;
import org.agrona.concurrent.SleepingIdleStrategy;
import org.agrona.concurrent.UnsafeBuffer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.aeron.samples.SampleConfiguration.*;

public class PublisherSubscriber {
    static String AERON_CHANNEL = "aeron:udp?endpoint=localhost:20122";
    static int NUMBER_OF_REQUEST = 10000;
    static int STREAM_ID = 1981;

    static int messagesConsumedCounter = 0;

    public static void main(String[] args) throws IOException {
        final MediaDriver driver = MediaDriver.launchEmbedded();
        String aeronDirectoryName = driver.aeronDirectoryName();
        System.out.println("Aeron folder: " + aeronDirectoryName);
        Aeron aeron = Aeron.connect(new Aeron.Context()
                .aeronDirectoryName(aeronDirectoryName)
                .availableImageHandler(SamplesUtil::printAvailableImage)
                .unavailableImageHandler(SamplesUtil::printUnavailableImage)
        );
        System.out.println("Client " + aeron.clientId());
        CompletableFuture.runAsync(() -> aeronSubscriber(aeron));
        aeronPublisher(aeron);
    }

    private static void aeronSubscriber(Aeron aeron) {
        Subscription subscription = aeron.addSubscription(AERON_CHANNEL, STREAM_ID);
        final FragmentHandler fragmentHandler = (buffer, offset, length, header) ->
        {
            final byte[] data = new byte[length];
            buffer.getBytes(offset, data);
            if (messagesConsumedCounter % 1000 == 0) {
                System.out.printf("%s Message received %n", messagesConsumedCounter);
            }
            messagesConsumedCounter += 1;
            if (messagesConsumedCounter == NUMBER_OF_REQUEST) {
                System.out.println("All Message received successfully");
            }
        };
        final AtomicBoolean running = new AtomicBoolean(true);
        SigInt.register(() -> running.set(false));
        SamplesUtil.subscriberLoop(fragmentHandler, FRAGMENT_COUNT_LIMIT, running).accept(subscription);
    }

    private static final UnsafeBuffer BUFFER = new UnsafeBuffer(BufferUtil.allocateDirectAligned(24615, 64));

    /**
     * Publisher using aero [udp] protocol, we use [IdleStrategy] to establish an idle time for
     * publisher connected to the [MediaDriver] and another one when consumer response [BACK_PRESSURED] error.
     */
    private static void aeronPublisher(Aeron aeron) throws IOException {
        final IdleStrategy idle = new SleepingIdleStrategy();
        final Publication publication = aeron.addPublication(AERON_CHANNEL, STREAM_ID);
        waitUntilIsConnected(idle, publication);
        var message = getBodyRequest();
        BUFFER.putBytes(0, message);
        var start = System.currentTimeMillis();
        for (long i = 0; i < NUMBER_OF_REQUEST; i++) {
            beckPressurePublication(idle, publication, message);
        }
        System.out.printf("Total time to send %s it was %s %n", NUMBER_OF_REQUEST, (System.currentTimeMillis() - start));

    }

    /**
     * We use again Idle in case we cannot send a record since the client cannot receive and return a [BACK_PRESSURED]
     * we set idle time [1 microsecond] default, and we retry.
     */
    private static void beckPressurePublication(IdleStrategy idle, Publication publication, byte[] message) {
        while (true) {
            long result = publication.offer(BUFFER, 0, message.length);
            if (!(result < 0)) break;
            processResult(result);
            idle.idle();
        }
    }

    /**
     * Function to wait the Publisher until connection is established
     */
    private static void waitUntilIsConnected(IdleStrategy idle, Publication publication) {
        while (!publication.isConnected()) {
            idle.idle();
        }
    }

    private static void processResult(long result) {
        if (result == Publication.BACK_PRESSURED) {
//            System.out.println("Offer failed due to back pressure");
        } else if (result == Publication.NOT_CONNECTED) {
            System.out.println("Offer failed because publisher is not connected to a subscriber");
        } else if (result == Publication.ADMIN_ACTION) {
            System.out.println("Offer failed because of an administration action in the system");
        } else if (result == Publication.CLOSED) {
            System.out.println("Offer failed because publication is closed");
        } else if (result == Publication.MAX_POSITION_EXCEEDED) {
            System.out.println("Offer failed due to publication reaching its max position");
        } else {
            System.out.println("Offer failed due to unknown reason: " + result);
        }
    }

    public static byte[] getBodyRequest() throws IOException {
        String resourceFileName = "body.xml"; // Replace with your XML file name
        try (InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourceFileName);
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            assert inputStream != null;
            inputStream.transferTo(buffer);
            return buffer.toByteArray();
        } catch (IOException e) {
            System.out.println("Error reading the file");
            throw e;
        }
    }
}
