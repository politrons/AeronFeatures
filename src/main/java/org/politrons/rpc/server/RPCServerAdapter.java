package org.politrons.rpc.server;


import com.aeroncookbook.sbe.MessageHeaderDecoder;
import com.aeroncookbook.sbe.MessageHeaderEncoder;
import com.aeroncookbook.sbe.RpcConnectRequestDecoder;
import com.aeroncookbook.sbe.RpcRequestMethodDecoder;
import com.aeroncookbook.sbe.RpcResponseEventEncoder;
import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.logbuffer.FragmentHandler;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableDirectByteBuffer;
import org.agrona.concurrent.ShutdownSignalBarrier;

import static org.agrona.CloseHelper.quietClose;

public class RPCServerAdapter implements FragmentHandler
{
    private final Aeron aeron;
    private final ShutdownSignalBarrier barrier;
    private final RpcConnectRequestDecoder connectRequest;
    private final RpcRequestMethodDecoder requestMethod;
    private final MessageHeaderEncoder headerEncoder;
    private final MessageHeaderDecoder headerDecoder;
    private final RpcResponseEventEncoder responseEvent;
    private final ExpandableDirectByteBuffer buffer;
    private Publication publication;

    public RPCServerAdapter(final Aeron aeron, final ShutdownSignalBarrier barrier)
    {
        this.connectRequest = new RpcConnectRequestDecoder();
        this.requestMethod = new RpcRequestMethodDecoder();
        this.responseEvent = new RpcResponseEventEncoder();
        this.headerDecoder = new MessageHeaderDecoder();
        this.headerEncoder = new MessageHeaderEncoder();
        this.buffer = new ExpandableDirectByteBuffer(512);
        this.aeron = aeron;
        this.barrier = barrier;
    }

    @Override
    public void onFragment(final DirectBuffer buffer, final int offset, final int length, final Header header)
    {
        headerDecoder.wrap(buffer, offset);
        final int headerLength = headerDecoder.encodedLength();
        final int actingLength = headerDecoder.blockLength();
        final int actingVersion = headerDecoder.version();

        switch (headerDecoder.templateId())
        {
            case RpcConnectRequestDecoder.TEMPLATE_ID:
                connectRequest.wrap(buffer, offset + headerLength,
                        actingLength, actingVersion);
                final int streamId = connectRequest.returnConnectStream();
                final String uri = connectRequest.returnConnectUri();
                blockingOpenConnection(streamId, uri);
                break;
            case RpcRequestMethodDecoder.TEMPLATE_ID:
                requestMethod.wrap(buffer, offset + headerLength,
                        actingLength, actingVersion);
                final String parameters = requestMethod.parameters();
                final String correlation = requestMethod.correlation();
                respond(parameters, correlation);
                break;
            default:
                break;
        }
    }

    private void respond(final String parameters, final String correlation)
    {
        final String returnValue = parameters.toUpperCase();

        System.out.printf("responding on correlation %s with value %s%n", correlation, returnValue);

        responseEvent.wrapAndApplyHeader(buffer, 0, headerEncoder);
        responseEvent.result(returnValue);
        responseEvent.correlation(correlation);

        int retries = 3;
        do
        {
            final long result = publication.offer(buffer, 0, headerEncoder.encodedLength() +
                    responseEvent.encodedLength());
            if (result > 0)
            {
                //shutdown once the result is sent
                barrier.signal();
                break;
            }
            else
            {
                System.out.printf("aeron returned %s%n", result);
            }
        }
        while (--retries > 0);
    }

    private void blockingOpenConnection(final int streamId, final String uri)
    {
        System.out.printf("Received connect request with response URI %s stream %s%n", uri, streamId);
        publication = aeron.addExclusivePublication(uri, streamId);
        while (!publication.isConnected())
        {
            aeron.context().idleStrategy().idle();
        }
    }

    public void closePublication()
    {
        quietClose(publication);
    }
}