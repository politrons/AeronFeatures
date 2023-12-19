# Aeron features

## Introduction

In this Proof of Concept (POC), we explored the capabilities of Aeron, a high-performance messaging library for Java, 
focusing on its publisher-subscriber model. The primary objective was to demonstrate the fundamental
workings of Aeron's messaging system and to introduce custom agents to ensure deterministic threading, 
thread safety, and improved reliability.

## Aeron's Publisher-Subscriber Model

Aeron employs a sophisticated publisher-subscriber model that facilitates efficient message passing.
In this POC, we established a basic Aeron setup where a publisher sends messages to a subscriber.
This model is the backbone of Aeron, allowing for high-throughput and low-latency communication between different components of an application.

* **[Publisher/Subscriber](src/main/java/org/politrons/PublisherSubscriber.java)**

## Aeron's Agents 

One of the critical enhancements in our POC was the introduction of custom agents.
These agents are responsible for managing threads in a more deterministic manner.
By controlling the execution context and scheduling of threads, 
we were able to reduce the likelihood of concurrency issues and race conditions,
which are common pitfalls in multi-threaded environments.

* **[Runner](src/main/java/org/politrons/agents/AeronAgent.java)**
* **[Publisher](src/main/java/org/politrons/agents/PublisherAgent.java)**
* **[Subscriber](src/main/java/org/politrons/agents/SubscriberAgent.java)**

## Benchmarks

In a recent benchmark test conducted on our Aeron-based messaging system, 
we achieved a remarkable feat of performance and efficiency.
The system successfully processed ```10,000 requests```, each with a message size of ```15 KB```, 
in less than ```800 milliseconds```. This test was designed to push the limits of Aeron's capabilities in high-throughput scenarios.