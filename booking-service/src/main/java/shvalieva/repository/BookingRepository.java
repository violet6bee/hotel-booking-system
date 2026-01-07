package shvalieva.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import shvalieva.entity.Booking;

import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    Optional<Booking> findByRequestId(String requestId);
    List<Booking> findByUserId(Long userId);
}