package co.edu.unicesar.rutavida.controller;

import co.edu.unicesar.rutavida.dto.ErrorKind;
import co.edu.unicesar.rutavida.dto.OperationResult;
import co.edu.unicesar.rutavida.dto.SimulationStatus;
import co.edu.unicesar.rutavida.model.UserSession;
import co.edu.unicesar.rutavida.model.enums.TrafficLevel;
import co.edu.unicesar.rutavida.service.AuthService;
import co.edu.unicesar.rutavida.simulation.SimulationEngine;
import co.edu.unicesar.rutavida.util.Messages;
import co.edu.unicesar.rutavida.util.ValidationUtil;

import java.util.Objects;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Control of the simulation for the view (CU-19, docs/api.md 10). Seeing its state needs
 * TRAFICO / CONSULTAR; starting, stopping it and forcing the traffic of a segment need
 * TRAFICO / GESTIONAR ("Registrar trafico y controlar el simulador").
 */
public class SimulationController {

    private static final Logger LOG = Logger.getLogger(SimulationController.class.getName());

    static final String MODULE = "TRAFICO";
    static final String MANAGE = "GESTIONAR";
    static final String VIEW = "CONSULTAR";

    private final SimulationEngine engine;
    private final AuthService authService;
    private final SessionContext session;

    public SimulationController(SimulationEngine engine, AuthService authService, SessionContext session) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.authService = Objects.requireNonNull(authService, "authService");
        this.session = Objects.requireNonNull(session, "session");
    }

    /** 10.1: the state of the simulation. */
    public OperationResult<SimulationStatus> status() {
        return run(VIEW, user -> currentStatus());
    }

    /** 10.2: starts it; if it already runs, answers its state. */
    public OperationResult<SimulationStatus> start() {
        return run(MANAGE, user -> {
            engine.start();
            return currentStatus();
        });
    }

    /** 10.2: stops it; if it is already stopped, answers its state. */
    public OperationResult<SimulationStatus> stop() {
        return run(MANAGE, user -> {
            engine.stop();
            return currentStatus();
        });
    }

    /** 10.3: the traffic level of a segment for the demo, applied in the next cycle. */
    public OperationResult<Void> forceTraffic(long segmentId, TrafficLevel level) {
        return run(MANAGE, user -> {
            ValidationUtil.requireNonNull(level, "nivel de tráfico");
            engine.forceCongestion(segmentId, level);
            return null;
        });
    }

    private SimulationStatus currentStatus() {
        return new SimulationStatus(engine.isRunning(), engine.startedAt(), engine.tickSeconds(), engine.source());
    }

    private <T> OperationResult<T> run(String action, Function<UserSession, T> work) {
        if (!session.isActive()) {
            return OperationResult.error(ErrorKind.AUTHENTICATION, Messages.SESSION_REQUIRED);
        }
        try {
            UserSession user = session.current();
            authService.requirePermission(user, MODULE, action);
            return OperationResult.ok(work.apply(user));
        } catch (RuntimeException e) {
            OperationResult<T> failure = OperationResult.failure(e);
            if (failure.error() == ErrorKind.UNEXPECTED || failure.error() == ErrorKind.UNAVAILABLE) {
                LOG.log(Level.WARNING, "Simulation operation failed", e);
            }
            return failure;
        }
    }
}
