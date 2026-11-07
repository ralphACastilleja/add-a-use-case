package team.projectpulse.student;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;

import java.util.Optional;

public class StudentLockRepositoryImpl implements StudentLockRepository {
    private final EntityManager entityManager;

    public StudentLockRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Optional<Student> findByIdForUpdate(Integer studentId) {
        return current(entityManager.createQuery("select s from Student s where s.id = :id", Student.class)
                .setParameter("id", studentId));
    }

    @Override
    public Optional<Student> findInDeactivationScopeForUpdate(Integer studentId, Integer callerId, boolean courseAdmin) {
        // Hibernate's FOR UPDATE OF student leaves joined scope rows as snapshot reads on
        // MySQL. Lock the complete scope query so a committed transfer/assignment is visible.
        String sql = courseAdmin ? """
                select s.id from peer_evaluation_user s
                join section cs on cs.section_id = s.section_section_id
                join course c on c.course_id = cs.course_course_id
                where s.id = :id and s.dtype = 'Student' and c.course_admin_id = :callerId
                for update
                """ : """
                select s.id from peer_evaluation_user s
                join section_instructor si on si.section_id = s.section_section_id
                where s.id = :id and s.dtype = 'Student' and si.instructor_id = :callerId
                for update
                """;
        boolean inScope = !entityManager.createNativeQuery(sql, Integer.class)
                .setParameter("id", studentId)
                .setParameter("callerId", callerId)
                .getResultList().isEmpty();
        return inScope ? findByIdForUpdate(studentId) : Optional.empty();
    }

    private Optional<Student> current(TypedQuery<Student> query) {
        Optional<Student> result = query.setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList().stream().findFirst();
        // DTO conversion may already have loaded the target. Hibernate's refresh after an
        // existing lock emits an ordinary SELECT here, which can read MySQL's old snapshot.
        // Evict only this unmodified target and hydrate it with a second FOR UPDATE query;
        // the database lock remains held throughout. Call this before mutating the target.
        if (result.isPresent()) {
            entityManager.detach(result.get());
            return query.getResultList().stream().findFirst();
        }
        return result;
    }
}
