import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import shvalieva.dto.RoomView;
import shvalieva.entity.Booking;
import shvalieva.repository.BookingRepository;
import shvalieva.service.BookingService;

import java.time.LocalDate;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

@SpringBootTest
@ContextConfiguration(initializers = BookingServiceTests.WiremockInitializer.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BookingServiceTests {

    static class WiremockInitializer
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        static final WireMockServer wireMockServer = new WireMockServer(0);

        @Override
        public void initialize(ConfigurableApplicationContext context) {
            wireMockServer.start();

            TestPropertyValues.of(
                    "hotel.base-url=http://localhost:" + wireMockServer.port(),
                    "hotel.timeout-ms=1000",
                    "hotel.retries=1"
            ).applyTo(context.getEnvironment());
        }
    }

    @Autowired
    BookingService bookingService;

    @Autowired
    BookingRepository bookingRepository;

    @BeforeEach
    void resetWiremock() {
        WiremockInitializer.wireMockServer.resetAll();
        bookingRepository.deleteAll();
    }

    @AfterAll
    void shutdown() {
        WiremockInitializer.wireMockServer.stop();
    }

    @Test
    void successFlow_confirmed() {
        stubFor(post(urlPathMatching("/rooms/\\d+/hold"))
                .willReturn(okJson("{}")));
        stubFor(post(urlPathMatching("/rooms/\\d+/confirm"))
                .willReturn(okJson("{}")));

        Booking booking = bookingService.createBooking(
                1L, 10L,
                LocalDate.now(),
                LocalDate.now().plusDays(1),
                "req-1"
        );

        Assertions.assertEquals(Booking.Status.CONFIRMED, booking.getStatus());
    }

    @Test
    void failureFlow_cancelledWithCompensation() {
        stubFor(post(urlPathMatching("/rooms/\\d+/hold"))
                .willReturn(serverError()));
        stubFor(post(urlPathMatching("/rooms/\\d+/release"))
                .willReturn(okJson("{}")));

        Booking booking = bookingService.createBooking(
                2L, 11L,
                LocalDate.now(),
                LocalDate.now().plusDays(1),
                "req-2"
        );

        Assertions.assertEquals(Booking.Status.CANCELLED, booking.getStatus());
    }

    @Test
    void timeoutFlow_cancelled() {
        stubFor(post(urlPathMatching("/rooms/\\d+/hold"))
                .willReturn(aResponse()
                        .withFixedDelay(2000)
                        .withStatus(200)));
        stubFor(post(urlPathMatching("/rooms/\\d+/release"))
                .willReturn(okJson("{}")));

        Booking booking = bookingService.createBooking(
                3L, 12L,
                LocalDate.now(),
                LocalDate.now().plusDays(1),
                "req-3"
        );

        Assertions.assertEquals(Booking.Status.CANCELLED, booking.getStatus());
    }

    @Test
    void idempotency_noDuplicate() {
        stubFor(post(urlPathMatching("/rooms/\\d+/hold"))
                .willReturn(okJson("{}")));
        stubFor(post(urlPathMatching("/rooms/\\d+/confirm"))
                .willReturn(okJson("{}")));

        Booking b1 = bookingService.createBooking(
                4L, 13L,
                LocalDate.now(),
                LocalDate.now().plusDays(1),
                "req-4"
        );

        Booking b2 = bookingService.createBooking(
                4L, 13L,
                LocalDate.now(),
                LocalDate.now().plusDays(1),
                "req-4"
        );

        Assertions.assertEquals(b1.getId(), b2.getId());
    }

    @Test
    void suggestions_sorted() {
        WiremockInitializer.wireMockServer.stubFor(
                get(urlEqualTo("/hotels/rooms"))
                        .willReturn(okJson("""
                                [
                                  {"id":1,"number":"101","timesBooked":5},
                                  {"id":2,"number":"102","timesBooked":1}
                                ]
                                """))
        );

        List<RoomView> result = bookingService.getRoomSuggestions();

        Assertions.assertEquals(2, result.size());
        Assertions.assertEquals(2L, result.get(0).id());
    }
}
