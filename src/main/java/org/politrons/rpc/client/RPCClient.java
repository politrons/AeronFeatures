package org.politrons.rpc.client;

import io.aeron.Aeron;
import io.aeron.CommonContext;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import org.agrona.CloseHelper;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.ShutdownSignalBarrier;
import org.agrona.concurrent.SleepingMillisIdleStrategy;
import org.politrons.rpc.server.RPCServerAgent;

public class RPCClient {

    public static void main(final String[] args)
    {
        final IdleStrategy idleStrategyClient = new SleepingMillisIdleStrategy();
        final ShutdownSignalBarrier barrier = new ShutdownSignalBarrier();

        //construct Media Driver, cleaning up media driver folder on start/stop
        final MediaDriver.Context mediaDriverCtx = new MediaDriver.Context()
                .aeronDirectoryName(CommonContext.getAeronDirectoryName() + "-client")
                .dirDeleteOnStart(true)
                .dirDeleteOnShutdown(true)
                .threadingMode(ThreadingMode.SHARED);
        final MediaDriver mediaDriver = MediaDriver.launchEmbedded(mediaDriverCtx);

        //construct Aeron, pointing at the media driver's folder
        final Aeron.Context aeronCtx = new Aeron.Context()
                .aeronDirectoryName(mediaDriver.aeronDirectoryName());
        final Aeron aeron = Aeron.connect(aeronCtx);

        System.out.println(mediaDriver.aeronDirectoryName());

        //Construct the client agent
        final RPCClientAgent clientAgent = new RPCClientAgent(aeron, barrier);
        final AgentRunner clientAgentRunner = new AgentRunner(idleStrategyClient, Throwable::printStackTrace,
                null, clientAgent);
        AgentRunner.startOnThread(clientAgentRunner);

        //Await shutdown signal
        barrier.await();

        CloseHelper.quietClose(clientAgentRunner);
        CloseHelper.quietClose(aeron);
        CloseHelper.quietClose(mediaDriver);
    }

}
