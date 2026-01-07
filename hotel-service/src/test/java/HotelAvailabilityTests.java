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

@SpringBootTest(classes = shvalieva.HotelServiceApplication.class)
public class HotelAvailabilityTests {

    @Autowired
    private HotelRepository hotelRepository;

    @Autowired
    private HotelService hotelService;

    @Test
    @Transactional
    void holdConfirmRelease_idempotentFlow() {
        Hotel h = new Hotel();
        h.setName("H");
        h.setCity("C");
        h = hotelRepository.save(h);
        Room r = new Room();
        r.setHotel(h);
        r.setNumber("101");
        r.setCapacity(2);
        r = hotelService.saveRoom(r);

        String req = "req-1";
        LocalDate s = LocalDate.now();
        LocalDate e = s.plusDays(2);

        RoomReservationLock l1 = hotelService.holdRoom(req, r.getId(), s, e);
        RoomReservationLock l2 = hotelService.holdRoom(req, r.getId(), s, e);
        Assertions.assertEquals(l1.getId(), l2.getId());

        hotelService.confirmHold(req);
        hotelService.confirmHold(req);

        RoomReservationLock afterConfirm = hotelService.confirmHold(req);
        Assertions.assertEquals(RoomReservationLock.Status.CONFIRMED, afterConfirm.getStatus());

        // Освобождение после подтверждения должно быть no-op согласно реализации
        RoomReservationLock afterRelease = hotelService.releaseHold(req);
        Assertions.assertEquals(RoomReservationLock.Status.CONFIRMED, afterRelease.getStatus());
    }
}