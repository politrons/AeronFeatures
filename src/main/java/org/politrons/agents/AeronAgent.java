package org.politrons.agents;

import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.driver.MediaDriver;
import io.aeron.samples.SamplesUtil;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.ShutdownSignalBarrier;

import java.io.IOException;

public class AeronAgent {


    static String AERON_CHANNEL = "aeron:udp?endpoint=localhost:20125";
    static int NUMBER_OF_REQUEST = 10000;
    static int STREAM_ID = 1981;

    public static void main(String[] args) throws IOException {
        final MediaDriver driver = MediaDriver.launchEmbedded();
        String aeronDirectoryName = driver.aeronDirectoryName();
        System.out.println("Aeron folder: " + aeronDirectoryName);
        Aeron aeron = Aeron.connect(new Aeron.Context()
                .aeronDirectoryName(aeronDirectoryName)
                .availableImageHandler(SamplesUtil::printAvailableImage)
                .unavailableImageHandler(SamplesUtil::printUnavailableImage)
        );
        //Idle Strategies
        final IdleStrategy idleStrategyPublisher = new BusySpinIdleStrategy();
        final IdleStrategy idleStrategySubscriber = new BusySpinIdleStrategy();

        final ShutdownSignalBarrier barrier = new ShutdownSignalBarrier();
        Subscription subscription = aeron.addSubscription(AERON_CHANNEL, STREAM_ID);
        SubscriberAgent subscriberAgent = new SubscriberAgent(subscription, barrier, NUMBER_OF_REQUEST);

        final Publication publication = aeron.addPublication(AERON_CHANNEL, STREAM_ID);
        PublisherAgent publisherAgent = new PublisherAgent(publication, NUMBER_OF_REQUEST);

       var subscriberRunner = new  AgentRunner(idleStrategySubscriber, t -> System.out.printf("Error in subscriber %s", t), null,subscriberAgent);
        AgentRunner.startOnThread(subscriberRunner);

        var publisherRunner = new  AgentRunner(idleStrategyPublisher, t -> System.out.printf("Error in publisher %s", t), null,publisherAgent);
        AgentRunner.startOnThread(publisherRunner);
    }

}
