package org.politrons.rpc.client;


import com.aeroncookbook.sbe.MessageHeaderEncoder;
import com.aeroncookbook.sbe.RpcConnectRequestEncoder;
import com.aeroncookbook.sbe.RpcRequestMethodEncoder;
import io.aeron.Aeron;
import io.aeron.ExclusivePublication;
import io.aeron.Subscription;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableDirectByteBuffer;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.ShutdownSignalBarrier;

import static java.util.UUID.randomUUID;
import static org.agrona.CloseHelper.quietClose;

public class RPCClientAgent implements Agent {
    private final ExpandableDirectByteBuffer buffer;
    private final Aeron aeron;
    private final RPCClientAdapter clientAdapter;
    private final RpcConnectRequestEncoder connectRequest;
    private final RpcRequestMethodEncoder requestMethod;
    private final MessageHeaderEncoder headerEncoder;

    private State state;
    private ExclusivePublication publication;
    private Subscription subscription;

    public static final int RPC_STREAM = 1;
    public static final String SERVER_URI = "aeron:udp?endpoint=127.0.0.1:2000";
    public static final String CLIENT_URI = "aeron:udp?endpoint=127.0.0.1:2001";

    public RPCClientAgent(final Aeron aeron, final ShutdownSignalBarrier barrier) {
        this.clientAdapter = new RPCClientAdapter(barrier);
        this.aeron = aeron;
        this.buffer = new ExpandableDirectByteBuffer(250);
        this.connectRequest = new RpcConnectRequestEncoder();
        this.requestMethod = new RpcRequestMethodEncoder();
        this.headerEncoder = new MessageHeaderEncoder();
    }

    @Override
    public void onStart() {
        System.out.println("Client starting");
        state = State.AWAITING_OUTBOUND_CONNECT;
        publication = aeron.addExclusivePublication(SERVER_URI, RPC_STREAM);
        subscription = aeron.addSubscription(CLIENT_URI, RPC_STREAM);
    }

    @Override
    public int doWork() {
        switch (state) {
            case AWAITING_OUTBOUND_CONNECT -> {
                awaitConnected();
                state = State.CONNECTED;
            }
            case CONNECTED -> {
                sendConnectRequest();
                state = State.AWAITING_INBOUND_CONNECT;
            }
            case AWAITING_INBOUND_CONNECT -> {
                awaitSubscriptionConnected();
                state = State.READY;
            }
            case READY -> {
                sendMessage();
                state = State.AWAITING_RESULT;
            }
            case AWAITING_RESULT -> subscription.poll(clientAdapter, 1);
            default -> {
            }
        }
        return 0;
    }

    private void sendMessage() {
        final String input = "string to be made uppercase";
        final String correlation = randomUUID().toString();
        requestMethod.wrapAndApplyHeader(buffer, 0, headerEncoder);
        requestMethod.parameters(input);
        requestMethod.correlation(correlation);

        System.out.printf("sending: %s with correlation %s%n", input, correlation);
        send(buffer, headerEncoder.encodedLength() + requestMethod.encodedLength());
    }

    private void sendConnectRequest() {
        connectRequest.wrapAndApplyHeader(buffer, 0, headerEncoder);
        connectRequest.returnConnectStream(RPC_STREAM);
        connectRequest.returnConnectUri(CLIENT_URI);

        send(buffer, headerEncoder.encodedLength() + connectRequest.encodedLength());
    }

    private void awaitSubscriptionConnected() {
        System.out.println("awaiting inbound server connect");

        while (!subscription.isConnected()) {
            aeron.context().idleStrategy().idle();
        }
    }

    private void awaitConnected() {
        System.out.println("awaiting outbound server connect");

        while (!publication.isConnected()) {
            aeron.context().idleStrategy().idle();
        }
    }

    @Override
    public void onClose() {
        quietClose(publication);
        quietClose(subscription);
    }

    @Override
    public String roleName() {
        return "client";
    }

    private void send(final DirectBuffer buffer, final int length) {
        int retries = 3;

        do {
            //in this example, the offset it always zero. This will not always be the case.
            final long result = publication.offer(buffer, 0, length);
            if (result > 0) {
                break;
            } else {
                System.out.printf("aeron returned %s on offer%n", result);
            }
        }
        while (--retries > 0);
    }

    enum State {
        AWAITING_OUTBOUND_CONNECT,
        CONNECTED,
        READY,
        AWAITING_RESULT,
        AWAITING_INBOUND_CONNECT
    }
}