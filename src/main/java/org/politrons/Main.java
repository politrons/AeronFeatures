package org.politrons;

import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.driver.MediaDriver;
import io.aeron.logbuffer.FragmentHandler;
import io.aeron.samples.SamplesUtil;
import org.agrona.BufferUtil;
import org.agrona.concurrent.SigInt;
import org.agrona.concurrent.UnsafeBuffer;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.aeron.samples.SampleConfiguration.*;

public class Main {
    public static void main(String[] args) throws InterruptedException {
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
        Subscription subscription = aeron.addSubscription(CHANNEL, STREAM_ID);
        final FragmentHandler fragmentHandler = (buffer, offset, length, header) ->
        {
            final byte[] data = new byte[length];
            buffer.getBytes(offset, data);
            System.out.printf("Message session %d (%d@%d) <<%s>>%n", header.sessionId(), length, offset, new String(data));
        };
        final AtomicBoolean running = new AtomicBoolean(true);
        SigInt.register(() -> running.set(false));
        SamplesUtil.subscriberLoop(fragmentHandler, FRAGMENT_COUNT_LIMIT, running).accept(subscription);
    }

    private static final UnsafeBuffer BUFFER = new UnsafeBuffer(BufferUtil.allocateDirectAligned(256, 64));

    private static void aeronPublisher(Aeron aeron) throws InterruptedException {
        final Publication publication = aeron.addPublication(CHANNEL, STREAM_ID);
        final String message = "Hello World!";
        BUFFER.putBytes(0, message.getBytes());
        for (long i = 0; i < NUMBER_OF_MESSAGES; i++) {
            final long result = publication.offer(BUFFER, 0, message.getBytes().length);
            System.out.println("Publisher result:" + result);
            if (result > 0) {
                System.out.println("yay!");
            } else if (result == Publication.BACK_PRESSURED) {
                System.out.println("Offer failed due to back pressure");
            } else if (result == Publication.NOT_CONNECTED) {
                System.out.println("Offer failed because publisher is not connected to a subscriber");
            } else if (result == Publication.ADMIN_ACTION) {
                System.out.println("Offer failed because of an administration action in the system");
            } else if (result == Publication.CLOSED) {
                System.out.println("Offer failed because publication is closed");
                break;
            } else if (result == Publication.MAX_POSITION_EXCEEDED) {
                System.out.println("Offer failed due to publication reaching its max position");
                break;
            } else {
                System.out.println("Offer failed due to unknown reason: " + result);
            }
        }
    }
}
