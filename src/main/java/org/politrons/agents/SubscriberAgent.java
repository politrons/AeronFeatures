package org.politrons.agents;

import io.aeron.Subscription;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.ShutdownSignalBarrier;

public class SubscriberAgent implements Agent {

    private final Subscription subscription;
    private final ShutdownSignalBarrier barrier;
    private int totalMessages;

    public SubscriberAgent(Subscription subscription, ShutdownSignalBarrier barrier, int totalMessages) {
        this.subscription = subscription;
        this.barrier = barrier;
        this.totalMessages = totalMessages;
    }

    @Override
    public void onStart() {
        System.out.println("Subscriber Agent up and running");
        Agent.super.onStart();
    }

    @Override
    public void onClose() {
        System.out.println("Subscriber Agent shutting down");
        Agent.super.onClose();
    }

    /**
     * Subscriber it will poll passing the handle the responsibility of process with the data formar
     * * [DirectBuffer] Data of the record, it contains different operators to obtain the data type.
     * * [Offset] Just like in Java we have the sequential counter of the record.
     * * [length] of the record, to read it properly in case of being big message.
     * * [Header] of the communication
     */
    @Override
    public int doWork() throws Exception {
        subscription.poll(this::handler, 1000);
        return 0;
    }

    private void handler(DirectBuffer buffer, int offset, int length, Header header) {
        final byte[] data = new byte[length];
        buffer.getBytes(offset, data);
        if (totalMessages % 1000 == 0) {
            System.out.printf("1000 Message received. %n");
        }
        totalMessages -= 1;
        if (totalMessages == 0) {
            System.out.println("Last Message received....");
            barrier.signal();
        }
    }

    @Override
    public String roleName() {
        return "Subscriber";
    }
}
