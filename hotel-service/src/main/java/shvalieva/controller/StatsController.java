package shvalieva.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import shvalieva.entity.Room;
import shvalieva.repository.RoomRepository;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/stats")
public class StatsController {
    private final RoomRepository roomRepository;

    public StatsController(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    @GetMapping("/rooms/popular")
    public List<Room> popularRooms() {
        return roomRepository.findAll().stream()
                .sorted(Comparator.comparingLong(Room::getTimesBooked).reversed())
                .toList();
    }
}