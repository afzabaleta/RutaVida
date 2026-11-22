package co.edu.unicesar.rutavida.controller;

import co.edu.unicesar.rutavida.dto.ErrorKind;
import co.edu.unicesar.rutavida.dto.OperationResult;
import co.edu.unicesar.rutavida.dto.OperationsReport;
import co.edu.unicesar.rutavida.service.ReportService;

import java.time.LocalDate;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The operations report for the web (RF-11). Like DashboardController, the permission of the
 * route (REPORTES / CONSULTAR) is checked by the web server before calling here.
 */
public class ReportController {

    private static final Logger LOG = Logger.getLogger(ReportController.class.getName());

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = Objects.requireNonNull(reportService, "reportService");
    }

    /** The report of the days {@code from} to {@code to}; nulls take the last 7 days. */
    public OperationResult<OperationsReport> operations(LocalDate from, LocalDate to) {
        try {
            return OperationResult.ok(reportService.operations(from, to));
        } catch (RuntimeException e) {
            OperationResult<OperationsReport> failure = OperationResult.failure(e);
            if (failure.error() == ErrorKind.UNEXPECTED || failure.error() == ErrorKind.UNAVAILABLE) {
                LOG.log(Level.WARNING, "Operations report failed", e);
            }
            return failure;
        }
    }
}
