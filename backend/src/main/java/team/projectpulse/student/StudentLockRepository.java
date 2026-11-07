package team.projectpulse.student;

import java.util.Optional;

/** Current reads shared by lifecycle changes and evaluation writes. Caller holds the transaction. */
public interface StudentLockRepository {
    Optional<Student> findByIdForUpdate(Integer studentId);
    Optional<Student> findInDeactivationScopeForUpdate(Integer studentId, Integer callerId, boolean courseAdmin);
}
