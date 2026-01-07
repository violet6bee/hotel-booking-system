package shvalieva.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import shvalieva.entity.Room;

public interface RoomRepository extends JpaRepository<Room, Long> {
}