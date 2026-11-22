package co.edu.unicesar.rutavida.controller;

import co.edu.unicesar.rutavida.dto.CitizenRequest;
import co.edu.unicesar.rutavida.dto.ErrorKind;
import co.edu.unicesar.rutavida.dto.OperationResult;
import co.edu.unicesar.rutavida.dto.UserView;
import co.edu.unicesar.rutavida.service.CitizenService;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A person registers to use the panic button (CU-23, DT-13). Needs no session: it is how a
 * citizen gets one. The web server limits how many registrations come from one network
 * address.
 */
public class CitizenController {

    private static final Logger LOG = Logger.getLogger(CitizenController.class.getName());

    static final String REGISTERED =
            "Su cuenta quedó creada. Inicie sesión una vez y su celular quedará conectado 30 días.";

    private final CitizenService citizenService;

    public CitizenController(CitizenService citizenService) {
        this.citizenService = Objects.requireNonNull(citizenService, "citizenService");
    }

    /** CU-23: the new account, without the password hash. */
    public OperationResult<UserView> register(CitizenRequest request) {
        try {
            return new OperationResult<>(true, REGISTERED, UserView.of(citizenService.register(request)));
        } catch (RuntimeException e) {
            OperationResult<UserView> failure = OperationResult.failure(e);
            if (failure.error() == ErrorKind.UNEXPECTED || failure.error() == ErrorKind.UNAVAILABLE) {
                LOG.log(Level.WARNING, "Citizen registration failed", e);
            }
            return failure;
        }
    }
}
