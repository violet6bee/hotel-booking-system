package shvalieva.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;
import shvalieva.dto.RoomView;
import shvalieva.entity.Booking;
import shvalieva.repository.BookingRepository;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final BookingRepository bookingRepository;
    private final WebClient webClient;
    private final int retries;
    private final Duration timeout;

    public BookingService(
            BookingRepository bookingRepository,
            WebClient.Builder webClientBuilder,
            @Value("${hotel.base-url}") String hotelBaseUrl,
            @Value("${hotel.timeout-ms:3000}") int timeoutMs,
            @Value("${hotel.retries:3}") int retries
    ) {
        this.bookingRepository = bookingRepository;
        this.webClient = webClientBuilder.baseUrl(hotelBaseUrl).build();
        this.retries = retries;
        this.timeout = Duration.ofMillis(timeoutMs);
    }

    @Transactional
    public Booking createBooking(
            Long userId,
            Long roomId,
            LocalDate start,
            LocalDate end,
            String requestId
    ) {

        Booking existing = bookingRepository.findByRequestId(requestId).orElse(null);
        if (existing != null) {
            return existing;
        }

        String correlationId = UUID.randomUUID().toString();

        Booking booking = new Booking();
        booking.setRequestId(requestId);
        booking.setUserId(userId);
        booking.setRoomId(roomId);
        booking.setStartDate(start);
        booking.setEndDate(end);
        booking.setStatus(Booking.Status.PENDING);
        booking.setCorrelationId(correlationId);
        booking.setCreatedAt(OffsetDateTime.now());

        bookingRepository.save(booking);
        log.info("[{}] Booking PENDING created", correlationId);

        Map<String, String> payload = Map.of(
                "requestId", requestId,
                "startDate", start.toString(),
                "endDate", end.toString()
        );

        try {
            callHotel("/rooms/" + roomId + "/hold", payload, correlationId);
            callHotel("/rooms/" + roomId + "/confirm", Map.of("requestId", requestId), correlationId);

            booking.setStatus(Booking.Status.CONFIRMED);
            bookingRepository.save(booking);

            log.info("[{}] Booking CONFIRMED", correlationId);

        } catch (Exception ex) {
            log.warn("[{}] Booking failed, compensating: {}", correlationId, ex.getMessage());

            try {
                callHotel("/rooms/" + roomId + "/release", Map.of("requestId", requestId), correlationId);
            } catch (Exception ignored) {}

            booking.setStatus(Booking.Status.CANCELLED);
            bookingRepository.save(booking);

            log.info("[{}] Booking CANCELLED", correlationId);
        }

        return booking;
    }

    private void callHotel(String path, Map<String, String> payload, String correlationId) {
        webClient.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .header("X-Correlation-Id", correlationId)
                .retrieve()
                .bodyToMono(Void.class)
                .timeout(timeout)
                .retryWhen(Retry.backoff(retries, Duration.ofMillis(300)))
                .block(timeout);
    }

    public List<RoomView> getRoomSuggestions() {
        List<RoomView> rooms = webClient.get()
                .uri("/hotels/rooms")
                .retrieve()
                .bodyToFlux(RoomView.class)
                .collectList()
                .block(timeout);

        if (rooms == null) {
            return List.of();
        }

        return rooms.stream()
                .sorted(
                        Comparator.comparingLong(RoomView::timesBooked)
                                .thenComparing(RoomView::id)
                )
                .toList();
    }
}
