package pl.commercelink.warehouse.api;

public interface ReservationService {
    Reservation create(Reservation reservation);
    void remove(Reservation Reservation);
}
