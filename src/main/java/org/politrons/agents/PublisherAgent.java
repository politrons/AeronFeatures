package org.politrons.agents;

import io.aeron.Publication;
import org.agrona.BufferUtil;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.UnsafeBuffer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class PublisherAgent implements Agent {

    private final Publication publication;
    private int totalMessages;

    private static final UnsafeBuffer BUFFER = new UnsafeBuffer(BufferUtil.allocateDirectAligned(24615, 64));


    public PublisherAgent(Publication publication, int totalMessages) {
        this.publication = publication;
        this.totalMessages = totalMessages;
    }

    @Override
    public void onStart() {
        System.out.println("Publisher Agent up and running");
        Agent.super.onStart();
    }

    @Override
    public void onClose() {
        System.out.println("Publisher Agent shutting down");
        Agent.super.onClose();
    }

    /**
     * We implement the [doWork] to do the logic of send the records.
     * if we're not yet connected to the [MediaDriver] or if I already sent all records, I
     * return 0, which means that the dutyCycle it will set the agent in [idle] state temporally.
     */
    @Override
    public int doWork() throws Exception {
        if (totalMessages > 0 && publication.isConnected()) {
            BUFFER.putBytes(0, getBodyRequest());
            if (publication.offer(BUFFER) > 0) {
                if (totalMessages % 1000 == 0) {
                    System.out.println("1000 records sent successfully");
                }
                totalMessages -= 1;
            }
        }
        return 0;
    }

    @Override
    public String roleName() {
        return "Publisher";
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
