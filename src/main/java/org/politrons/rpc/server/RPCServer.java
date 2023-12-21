package org.politrons.rpc.server;

import io.aeron.Aeron;
import io.aeron.CommonContext;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import org.agrona.CloseHelper;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.ShutdownSignalBarrier;
import org.agrona.concurrent.SleepingMillisIdleStrategy;

public class RPCServer {

    public static void main(final String[] args)
    {
        final IdleStrategy idleStrategy = new SleepingMillisIdleStrategy();
        final ShutdownSignalBarrier barrier = new ShutdownSignalBarrier();

        //construct Media Driver, cleaning up media driver folder on start/stop
        final MediaDriver.Context mediaDriverCtx = new MediaDriver.Context()
                .aeronDirectoryName(CommonContext.getAeronDirectoryName() + "-server")
                .dirDeleteOnStart(true)
                .dirDeleteOnShutdown(true)
                .threadingMode(ThreadingMode.SHARED);
        final MediaDriver mediaDriver = MediaDriver.launchEmbedded(mediaDriverCtx);

        //construct Aeron, pointing at the media driver's folder
        final Aeron.Context aeronCtx = new Aeron.Context()
                .aeronDirectoryName(mediaDriver.aeronDirectoryName());
        final Aeron aeron = Aeron.connect(aeronCtx);


        //Construct the server agent
        final RPCServerAgent serverAgent = new RPCServerAgent(aeron, barrier);
        final AgentRunner serverAgentRunner = new AgentRunner(idleStrategy, Throwable::printStackTrace,
                null, serverAgent);
        AgentRunner.startOnThread(serverAgentRunner);

        //Await shutdown signal
        barrier.await();

        CloseHelper.quietClose(serverAgentRunner);
        CloseHelper.quietClose(aeron);
        CloseHelper.quietClose(mediaDriver);
    }

}
