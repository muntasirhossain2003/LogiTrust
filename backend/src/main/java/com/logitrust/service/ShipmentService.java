package com.logitrust.service;

import com.logitrust.domain.Role;
import com.logitrust.domain.Shipment;
import com.logitrust.domain.ShipmentItem;
import com.logitrust.domain.ShipmentStatus;
import com.logitrust.domain.User;
import com.logitrust.dto.CreateShipmentRequest;
import com.logitrust.exception.ForbiddenOperationException;
import com.logitrust.exception.IllegalStateTransitionException;
import com.logitrust.exception.ShipmentNotFoundException;
import com.logitrust.repository.ShipmentItemRepository;
import com.logitrust.repository.ShipmentRepository;
import com.logitrust.repository.UserRepository;
import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShipmentService {

    private static final String TRACKING_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int TRACKING_LENGTH = 10;

    private final ShipmentRepository shipmentRepository;
    private final ShipmentItemRepository shipmentItemRepository;
    private final UserRepository userRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public ShipmentService(
            ShipmentRepository shipmentRepository,
            ShipmentItemRepository shipmentItemRepository,
            UserRepository userRepository) {
        this.shipmentRepository = shipmentRepository;
        this.shipmentItemRepository = shipmentItemRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Shipment createShipment(UUID manufacturerId, CreateShipmentRequest request) {
        User manufacturer = loadUser(manufacturerId);

        User destinationParty = null;
        if (request.destinationPartyEmail() != null && !request.destinationPartyEmail().isBlank()) {
            destinationParty = userRepository.findByEmail(request.destinationPartyEmail())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No account found for destination party " + request.destinationPartyEmail()));
            if (destinationParty.getRole() != Role.DISTRIBUTOR && destinationParty.getRole() != Role.RETAILER) {
                throw new IllegalArgumentException("Destination party must be a distributor or retailer.");
            }
        }

        Shipment shipment = Shipment.builder()
                .manufacturer(manufacturer)
                .destinationParty(destinationParty)
                .trackingCode(generateTrackingCode())
                .originLabel(request.originLabel())
                .destinationLabel(request.destinationLabel())
                .build();

        for (CreateShipmentRequest.Item item : request.items()) {
            if (shipmentItemRepository.existsBySerialNumber(item.serialNumber())) {
                throw new IllegalArgumentException(
                        "Serial number already registered on the platform: " + item.serialNumber());
            }
            shipment.addItem(ShipmentItem.builder()
                    .serialNumber(item.serialNumber())
                    .qrCode("LT:" + shipment.getTrackingCode() + ":" + item.serialNumber())
                    .productName(item.productName())
                    .productCategory(item.productCategory())
                    .build());
        }

        return shipmentRepository.save(shipment);
    }

    @Transactional
    public Shipment assignCourier(UUID actorId, UUID shipmentId, String courierEmail) {
        User actor = loadUser(actorId);
        Shipment shipment = loadShipment(shipmentId);

        boolean isOwningManufacturer = shipment.getManufacturer().getId().equals(actor.getId());
        boolean isDistributor = actor.getRole() == Role.DISTRIBUTOR;
        if (!isOwningManufacturer && !isDistributor) {
            throw new ForbiddenOperationException(
                    "Only the owning manufacturer or a distributor can assign a courier.");
        }

        User courier = userRepository.findByEmail(courierEmail)
                .orElseThrow(() -> new IllegalArgumentException("No account found for courier " + courierEmail));
        if (courier.getRole() != Role.COURIER) {
            throw new IllegalArgumentException(courierEmail + " is not a courier account.");
        }

        transition(shipment, ShipmentStatus.ASSIGNED);
        shipment.setCurrentCourier(courier);
        return shipmentRepository.save(shipment);
    }

    @Transactional
    public Shipment updateTransitStatus(UUID actorId, UUID shipmentId, ShipmentStatus target) {
        if (target != ShipmentStatus.IN_TRANSIT && target != ShipmentStatus.AT_CHECKPOINT) {
            throw new IllegalStateTransitionException(
                    "Couriers can only move a shipment to IN_TRANSIT or AT_CHECKPOINT.");
        }

        User actor = loadUser(actorId);
        Shipment shipment = loadShipment(shipmentId);

        if (shipment.getCurrentCourier() == null
                || !shipment.getCurrentCourier().getId().equals(actor.getId())) {
            throw new ForbiddenOperationException("Only the assigned courier can update transit status.");
        }

        transition(shipment, target);
        return shipmentRepository.save(shipment);
    }

    @Transactional
    public Shipment confirmHandoff(UUID actorId, UUID shipmentId) {
        User actor = loadUser(actorId);
        Shipment shipment = loadShipment(shipmentId);

        // FR-2.5: state only advances on digital acceptance by the receiving
        // party. If no destination party was pre-declared, any distributor or
        // retailer may claim receipt (and becomes the recorded receiver).
        if (shipment.getDestinationParty() != null) {
            if (!shipment.getDestinationParty().getId().equals(actor.getId())) {
                throw new ForbiddenOperationException(
                        "Only the declared destination party can confirm this handoff.");
            }
        } else {
            if (actor.getRole() != Role.DISTRIBUTOR && actor.getRole() != Role.RETAILER) {
                throw new ForbiddenOperationException(
                        "Only a distributor or retailer can confirm receipt.");
            }
            shipment.setDestinationParty(actor);
        }

        transition(shipment, ShipmentStatus.DELIVERED);
        return shipmentRepository.save(shipment);
    }

    @Transactional
    public Shipment dispute(UUID actorId, UUID shipmentId) {
        User actor = loadUser(actorId);
        Shipment shipment = loadShipment(shipmentId);

        boolean involved = shipment.getManufacturer().getId().equals(actor.getId())
                || (shipment.getDestinationParty() != null
                        && shipment.getDestinationParty().getId().equals(actor.getId()))
                || actor.getRole() == Role.ADMIN;
        if (!involved) {
            throw new ForbiddenOperationException("Only involved parties can dispute a shipment.");
        }

        transition(shipment, ShipmentStatus.DISPUTED);
        return shipmentRepository.save(shipment);
    }

    @Transactional(readOnly = true)
    public List<Shipment> listForUser(UUID actorId) {
        User actor = loadUser(actorId);
        return switch (actor.getRole()) {
            case MANUFACTURER -> shipmentRepository.findAllByManufacturerOrderByCreatedAtDesc(actor);
            case COURIER -> shipmentRepository.findAllByCurrentCourierOrderByCreatedAtDesc(actor);
            case DISTRIBUTOR, RETAILER -> shipmentRepository.findAllByDestinationPartyOrderByCreatedAtDesc(actor);
            case ADMIN -> shipmentRepository.findAll();
            case CUSTOMER -> List.of();
        };
    }

    @Transactional(readOnly = true)
    public Shipment getForUser(UUID actorId, UUID shipmentId) {
        User actor = loadUser(actorId);
        Shipment shipment = loadShipment(shipmentId);
        boolean involved = shipment.getManufacturer().getId().equals(actor.getId())
                || (shipment.getCurrentCourier() != null
                        && shipment.getCurrentCourier().getId().equals(actor.getId()))
                || (shipment.getDestinationParty() != null
                        && shipment.getDestinationParty().getId().equals(actor.getId()))
                || actor.getRole() == Role.ADMIN;
        if (!involved) {
            throw new ForbiddenOperationException("You are not a party to this shipment.");
        }
        return shipment;
    }

    @Transactional(readOnly = true)
    public Shipment getByTrackingCode(String trackingCode) {
        return shipmentRepository.findByTrackingCode(trackingCode)
                .orElseThrow(ShipmentNotFoundException::new);
    }

    /**
     * Single choke point for every status change (FR-2.3): consults the enum's
     * legal-transition map and refuses to touch frozen shipments.
     */
    private void transition(Shipment shipment, ShipmentStatus target) {
        if (shipment.isFrozen()) {
            throw new IllegalStateTransitionException(
                    "Shipment is frozen pending fraud review; no state changes allowed.");
        }
        if (!shipment.getStatus().canTransitionTo(target)) {
            throw new IllegalStateTransitionException(shipment.getStatus(), target);
        }
        shipment.setStatus(target);
    }

    private User loadUser(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ForbiddenOperationException("Account no longer exists."));
    }

    private Shipment loadShipment(UUID id) {
        return shipmentRepository.findById(id).orElseThrow(ShipmentNotFoundException::new);
    }

    private String generateTrackingCode() {
        for (int attempt = 0; attempt < 5; attempt++) {
            StringBuilder sb = new StringBuilder("LT-");
            for (int i = 0; i < TRACKING_LENGTH; i++) {
                sb.append(TRACKING_ALPHABET.charAt(secureRandom.nextInt(TRACKING_ALPHABET.length())));
            }
            String code = sb.toString();
            if (!shipmentRepository.existsByTrackingCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Could not generate a unique tracking code.");
    }
}
