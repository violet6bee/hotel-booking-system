import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import shvalieva.entity.Hotel;
import shvalieva.entity.Room;
import shvalieva.entity.RoomReservationLock;
import shvalieva.repository.HotelRepository;
import shvalieva.service.HotelService;

import java.time.LocalDate;
import java.util.List;

@SpringBootTest(classes = shvalieva.HotelServiceApplication.class)
public class HotelMoreTests {

    @Autowired
    private HotelRepository hotelRepository;

    @Autowired
    private HotelService hotelService;

    @Test
    @Transactional
    void dateConflictReturns409LikeBehavior() {
        Hotel h = new Hotel();
        h.setName("H");
        h.setCity("C");
        h = hotelRepository.save(h);
        Room r = new Room();
        r.setHotel(h);
        r.setNumber("101");
        r.setCapacity(2);
        r = hotelService.saveRoom(r);

        final LocalDate s1 = LocalDate.now();
        final LocalDate e1 = s1.plusDays(2);
        final Room room = r;
        hotelService.holdRoom("req-a", r.getId(), s1, e1);

        // пересечение дат
        Assertions.assertThrows(IllegalStateException.class, () ->
                hotelService.holdRoom("req-b", room.getId(), s1.plusDays(1), e1.plusDays(1))
        );
    }

    @Test
    @Transactional
    void availableFlagDoesNotAffectDateOccupancy() {
        Hotel h = new Hotel();
        h.setName("H");
        h.setCity("C");
        h = hotelRepository.save(h);
        Room r = new Room();
        r.setHotel(h);
        r.setNumber("102");
        r.setCapacity(2);
        r.setAvailable(false);
        r = hotelService.saveRoom(r);

        // Даже если available=false, занятость по датам определяется блокировками/бронями
        LocalDate s1 = LocalDate.now();
        LocalDate e1 = s1.plusDays(1);
        RoomReservationLock lock = hotelService.holdRoom("req-c", r.getId(), s1, e1);
        Assertions.assertEquals(RoomReservationLock.Status.HELD, lock.getStatus());
    }

    @Test
    @Transactional
    void statsPopularRoomsOrder() {
        Hotel h = new Hotel();
        h.setName("H");
        h.setCity("C");
        h = hotelRepository.save(h);

        Room r1 = new Room();
        r1.setHotel(h);
        r1.setNumber("201");
        r1.setCapacity(2);
        r1 = hotelService.saveRoom(r1);

        Room r2 = new Room();
        r2.setHotel(h);
        r2.setNumber("202");
        r2.setCapacity(2);
        r2 = hotelService.saveRoom(r2);

        hotelService.holdRoom("req-d", r1.getId(), LocalDate.now(), LocalDate.now().plusDays(1));
        hotelService.confirmHold("req-d");
        hotelService.holdRoom("req-e", r1.getId(), LocalDate.now().plusDays(2), LocalDate.now().plusDays(3));
        hotelService.confirmHold("req-e");

        hotelService.holdRoom("req-f", r2.getId(), LocalDate.now(), LocalDate.now().plusDays(1));
        hotelService.confirmHold("req-f");

        List<Room> popular = hotelService.popularRooms();

        Assertions.assertEquals(r1.getId(), popular.get(0).getId());
        Assertions.assertEquals(r2.getId(), popular.get(1).getId());
    }

}