package co.edu.unicesar.rutavida.app;

import co.edu.unicesar.rutavida.dto.OperationResult;
import co.edu.unicesar.rutavida.model.User;
import co.edu.unicesar.rutavida.model.UserSession;
import co.edu.unicesar.rutavida.service.AdminBootstrapService;
import co.edu.unicesar.rutavida.util.Messages;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Development tool: creates an ADMINISTRADOR in the local Oracle with the password stored
 * as a PBKDF2 hash, and then checks that it can log in (LOGIN / EXITO in AUDITORIA).
 *
 * <p>The password is asked on the console, never as an argument (arguments stay in the
 * command history). It is kept in char[] and wiped after use. Run it from IntelliJ with
 * backend/ as working directory, after creating backend/db.properties.
 * Exit code 0 = OK, 1 = problem found.
 */
public final class CreateAdminUser {

    private static final Console CONSOLE = System.console();
    private static final BufferedReader INPUT =
            new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

    private CreateAdminUser() {
    }

    public static void main(String[] args) {
        System.out.println("RutaVida - Crear administrador");
        if (CONSOLE == null) {
            System.out.println("[AVISO] Esta consola no oculta lo que se escribe: la contraseña se verá"
                    + " en pantalla. Úselo solo en su computador.");
        }

        AppContext context;
        try {
            context = AppContext.create();
        } catch (IllegalStateException e) {
            System.err.println("[ERROR] Configuración incompleta: " + e.getMessage());
            System.err.println("        Copie db.properties.example como db.properties y escriba la contraseña.");
            System.exit(1);
            return;
        }

        try {
            AdminBootstrapService admins = context.adminBootstrap();
            long existing = admins.countAdmins();
            if (existing > 0) {
                System.out.println("[AVISO] Ya hay " + existing + " administrador(es) en la base de datos.");
                if (!ask("¿Crear otro? (s/n)").equalsIgnoreCase("s")) {
                    System.out.println("No se creó ningún usuario.");
                    return;
                }
            }

            String document = ask("Documento (letras y números, de 5 a 20)");
            String firstNames = ask("Nombres");
            String lastNames = ask("Apellidos");
            String email = ask("Correo");
            String username = ask("Usuario (letras, números, punto o guion bajo; de 4 a 40)");
            char[] password = askPassword("Contraseña (mínimo 10 caracteres, con letras y números)");
            char[] confirmation = askPassword("Repita la contraseña");

            try {
                if (!Arrays.equals(password, confirmation)) {
                    System.err.println("[ERROR] Las contraseñas no coinciden. No se creó ningún usuario.");
                    System.exit(1);
                    return;
                }
                User admin = admins.createAdmin(document, firstNames, lastNames, email, username, password);
                System.out.println("[OK] Administrador creado: id " + admin.getId()
                        + ", usuario " + admin.getUsername());

                // End-to-end check of A3: the new administrator logs in through the controller.
                OperationResult<UserSession> login = context.loginController().login(admin.getUsername(), confirmation);
                if (!login.success()) {
                    System.err.println("[ERROR] El usuario se creó pero no pudo iniciar sesión: " + login.message());
                    System.exit(1);
                    return;
                }
                System.out.println("[OK] Inicio de sesión comprobado: quedó LOGIN / EXITO en AUDITORIA.");
                context.loginController().logout();
            } finally {
                Arrays.fill(password, '\0');
                Arrays.fill(confirmation, '\0');
            }
        } catch (RuntimeException e) {
            System.err.println("[ERROR] " + Messages.userMessage(e));
            System.exit(1);
        }
    }

    private static String ask(String label) {
        if (CONSOLE != null) {
            String line = CONSOLE.readLine("%s: ", label);
            return line == null ? "" : line.trim();
        }
        return readLine(label).trim();
    }

    private static char[] askPassword(String label) {
        if (CONSOLE != null) {
            char[] typed = CONSOLE.readPassword("%s: ", label);
            return typed == null ? new char[0] : typed;
        }
        return readLine(label).toCharArray();
    }

    private static String readLine(String label) {
        System.out.print(label + ": ");
        System.out.flush();
        try {
            String line = INPUT.readLine();
            return line == null ? "" : line;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}