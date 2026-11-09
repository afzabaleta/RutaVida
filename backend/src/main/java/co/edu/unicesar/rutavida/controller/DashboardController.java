package co.edu.unicesar.rutavida.controller;

import co.edu.unicesar.rutavida.dto.DashboardSummary;
import co.edu.unicesar.rutavida.dto.OperationResult;
import co.edu.unicesar.rutavida.service.DashboardService;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Indicators of the main panel for the web (CU-16). Like CatalogController, the permission
 * of the route (REPORTES / CONSULTAR) is checked by the web server before calling here.
 */
public class DashboardController {

    private static final Logger LOG = Logger.getLogger(DashboardController.class.getName());

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = Objects.requireNonNull(dashboardService, "dashboardService");
    }

    public OperationResult<DashboardSummary> summary() {
        try {
            return OperationResult.ok(dashboardService.getSummary());
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Dashboard query failed", e);
            return OperationResult.failure(e);
        }
    }
}