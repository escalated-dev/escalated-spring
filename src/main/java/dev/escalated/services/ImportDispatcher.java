package dev.escalated.services;

/**
 * Queues a started or resumed import job. The default is {@link ImportRunner}, a thread in this
 * process; a host with a job queue declares its own {@code @Primary} implementation and calls
 * {@link ImportJobService#run(String)} from its worker.
 */
public interface ImportDispatcher {

    void dispatch(String jobId);
}
