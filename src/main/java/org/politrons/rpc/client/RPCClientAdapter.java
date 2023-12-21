package org.politrons.rpc.client;

import com.aeroncookbook.sbe.MessageHeaderDecoder;
import com.aeroncookbook.sbe.RpcResponseEventDecoder;
import com.aeroncookbook.sbe.RpcResponseEventEncoder;
import io.aeron.logbuffer.FragmentHandler;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.ShutdownSignalBarrier;

public class RPCClientAdapter implements FragmentHandler

{ private final RpcResponseEventDecoder responseEvent;
    private final MessageHeaderDecoder headerDecoder;
    private final ShutdownSignalBarrier barrier;

    public RPCClientAdapter(final ShutdownSignalBarrier barrier)
    {
        this.barrier = barrier;
        this.responseEvent = new RpcResponseEventDecoder();
        this.headerDecoder = new MessageHeaderDecoder();
    }

    @Override
    public void onFragment(final DirectBuffer buffer, final int offset, final int length, final Header header)
    {
        headerDecoder.wrap(buffer, offset);

        if (headerDecoder.templateId() == RpcResponseEventEncoder.TEMPLATE_ID)
        {
            responseEvent.wrap(buffer, offset + headerDecoder.encodedLength(),
                    headerDecoder.blockLength(), headerDecoder.version());
            System.out.printf("Received %s%n", responseEvent.result());
            barrier.signal();
        }
        else
        {
            System.out.println("Unknown message");
        }
    }
}