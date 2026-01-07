package shvalieva.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import shvalieva.entity.Hotel;

public interface HotelRepository extends JpaRepository<Hotel, Long> {
}