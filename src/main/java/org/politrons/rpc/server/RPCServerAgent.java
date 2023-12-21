package org.politrons.rpc.server;

import io.aeron.Aeron;
import io.aeron.Subscription;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.ShutdownSignalBarrier;

public class RPCServerAgent implements Agent
{
    private final Aeron aeron;
    private final RPCServerAdapter serverAdapter;
    private Subscription subscription;

    public static final int RPC_STREAM = 1;
    public static final String SERVER_URI = "aeron:udp?endpoint=127.0.0.1:2000";

    public RPCServerAgent(final Aeron aeron, final ShutdownSignalBarrier barrier)
    {
        this.aeron = aeron;
        this.serverAdapter = new RPCServerAdapter(aeron, barrier);
    }

    @Override
    public void onStart()
    {
        System.out.println("Server starting");
        subscription = aeron.addSubscription(SERVER_URI, RPC_STREAM);
    }

    @Override
    public int doWork() throws Exception
    {
        return subscription.poll(serverAdapter, 1);
    }

    @Override
    public void onClose()
    {
        serverAdapter.closePublication();
    }

    @Override
    public String roleName()
    {
        return "server";
    }
}