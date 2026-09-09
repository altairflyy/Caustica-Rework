package dev.comfyfluffy.caustica.rt;

/** Small testable state authority for the executor's two-phase shutdown. */
final class GpuShutdownState {
    private Phase phase = Phase.RUNNING;

    synchronized void requireSubmissionAllowed() {
        if (phase != Phase.RUNNING) {
            throw new IllegalStateException("RT GPU executor is stopping");
        }
    }

    synchronized boolean beginQuiescence() {
        if (phase == Phase.RUNNING) {
            phase = Phase.STOPPING;
            return true;
        }
        return false;
    }

    synchronized void markQuiesced() {
        if (phase != Phase.STOPPING) {
            throw new IllegalStateException("RT GPU executor was not stopped before quiescence");
        }
        phase = Phase.QUIESCED;
    }

    synchronized boolean isQuiesced() {
        return phase == Phase.QUIESCED || phase == Phase.DESTROYED;
    }

    synchronized boolean shouldDestroyInfrastructure() {
        if (phase == Phase.DESTROYED) {
            return false;
        }
        if (phase != Phase.QUIESCED) {
            throw new IllegalStateException("RT GPU executor must be quiesced before final destruction");
        }
        return true;
    }

    synchronized void markDestroyed() {
        if (phase != Phase.QUIESCED) {
            throw new IllegalStateException("RT GPU executor infrastructure cannot be marked destroyed");
        }
        phase = Phase.DESTROYED;
    }

    private enum Phase {
        RUNNING,
        STOPPING,
        QUIESCED,
        DESTROYED
    }
}
