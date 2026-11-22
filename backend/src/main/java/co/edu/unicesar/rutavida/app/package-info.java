/**
 * <b>Responsibility.</b> Composition root: builds every object and injects its dependencies (manual dependency injection, no framework). Also holds the application entry points.
 *
 * <p><b>Allowed dependencies.</b> May depend on every package. It is the ONLY package outside {@code dao} allowed to know {@code dao.jdbc} implementations.
 *
 * <p><b>Principles.</b> GRASP Pure Fabrication / SOLID Dependency Inversion: services receive interfaces, and this package decides which implementation is used.
 *
 * <p><b>Owner.</b> Andrés
 */
package co.edu.unicesar.rutavida.app;
