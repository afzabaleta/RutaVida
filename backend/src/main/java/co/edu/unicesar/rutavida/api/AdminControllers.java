package co.edu.unicesar.rutavida.api;

import co.edu.unicesar.rutavida.controller.AuditController;
import co.edu.unicesar.rutavida.controller.FleetController;
import co.edu.unicesar.rutavida.controller.HospitalController;
import co.edu.unicesar.rutavida.controller.UserController;

import java.util.Objects;

/**
 * The controllers of the administration screens (A17): users and roles (CU-03), units and
 * stations (CU-04) and hospitals (CU-05). Grouped so ApiServer receives them as one argument.
 */
public record AdminControllers(UserController users, FleetController fleet, HospitalController hospitals,
                               AuditController audit) {

    public AdminControllers {
        Objects.requireNonNull(users, "users");
        Objects.requireNonNull(fleet, "fleet");
        Objects.requireNonNull(hospitals, "hospitals");
        Objects.requireNonNull(audit, "audit");
    }
}
