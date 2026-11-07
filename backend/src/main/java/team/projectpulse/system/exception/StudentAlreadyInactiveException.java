package team.projectpulse.system.exception;

public class StudentAlreadyInactiveException extends RuntimeException {
    public StudentAlreadyInactiveException() {
        super("This student is already deactivated.");
    }
}
