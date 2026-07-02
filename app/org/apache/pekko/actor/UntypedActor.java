package org.apache.pekko.actor;

/**
 * Small bridge for legacy Akka-style actors while running on Apache Pekko.
 */
public abstract class UntypedActor extends AbstractActor {
    public abstract void onReceive(Object message) throws Throwable;

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .matchAny(message -> {
                    try {
                        onReceive(message);
                    } catch (RuntimeException e) {
                        throw e;
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    }
                })
                .build();
    }
}
