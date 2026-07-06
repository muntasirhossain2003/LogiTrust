package com.logitrust.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.logitrust.domain.ProductCategory;
import com.logitrust.domain.Role;
import com.logitrust.domain.Shipment;
import com.logitrust.domain.ShipmentStatus;
import com.logitrust.domain.User;
import com.logitrust.dto.CreateShipmentRequest;
import com.logitrust.dto.TransitUpdateRequest;
import com.logitrust.exception.ForbiddenOperationException;
import com.logitrust.exception.IllegalStateTransitionException;
import com.logitrust.repository.ShipmentItemRepository;
import com.logitrust.repository.ShipmentRepository;
import com.logitrust.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ShipmentServiceTest {

    private ShipmentRepository shipmentRepository;
    private ShipmentItemRepository shipmentItemRepository;
    private UserRepository userRepository;
    private CustodyChainService custodyChainService;
    private ShipmentService service;

    private User manufacturer;
    private User distributor;
    private User courier;
    private User retailer;

    @BeforeEach
    void setUp() {
        shipmentRepository = mock(ShipmentRepository.class);
        shipmentItemRepository = mock(ShipmentItemRepository.class);
        userRepository = mock(UserRepository.class);
        custodyChainService = mock(CustodyChainService.class);
        service = new ShipmentService(shipmentRepository, shipmentItemRepository, userRepository, custodyChainService);

        when(shipmentRepository.save(any(Shipment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(shipmentRepository.existsByTrackingCode(any())).thenReturn(false);
        when(shipmentItemRepository.existsBySerialNumber(any())).thenReturn(false);

        manufacturer = user(Role.MANUFACTURER, "mfg@t.dev");
        distributor = user(Role.DISTRIBUTOR, "dist@t.dev");
        courier = user(Role.COURIER, "courier@t.dev");
        retailer = user(Role.RETAILER, "retail@t.dev");
    }

    private User user(Role role, String email) {
        User u = User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .passwordHash("x")
                .role(role)
                .createdAt(Instant.now())
                .build();
        when(userRepository.findById(u.getId())).thenReturn(Optional.of(u));
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(u));
        return u;
    }

    private Shipment shipmentInState(ShipmentStatus status) {
        Shipment s = Shipment.builder()
                .id(UUID.randomUUID())
                .manufacturer(manufacturer)
                .trackingCode("LT-TEST123456")
                .status(status)
                .originLabel("Factory A")
                .destinationLabel("Store B")
                .build();
        when(shipmentRepository.findById(s.getId())).thenReturn(Optional.of(s));
        return s;
    }

    private CreateShipmentRequest createRequest() {
        return new CreateShipmentRequest(
                "Factory A", "Store B", null,
                List.of(new CreateShipmentRequest.Item("SN-001", "Insulin pack", ProductCategory.PHARMA)), null);
    }

    private TransitUpdateRequest transitRequest(ShipmentStatus status) {
        return new TransitUpdateRequest(status, "Checkpoint A", null, null, null);
    }

    // ---------- create ----------

    @Test
    void create_generatesTrackingCode_andQrPerItem() {
        Shipment s = service.createShipment(manufacturer.getId(), createRequest());

        assertThat(s.getTrackingCode()).startsWith("LT-").hasSize(13);
        assertThat(s.getStatus()).isEqualTo(ShipmentStatus.CREATED);
        assertThat(s.getItems()).hasSize(1);
        assertThat(s.getItems().get(0).getQrCode())
                .isEqualTo("LT:" + s.getTrackingCode() + ":SN-001");
    }

    @Test
    void create_rejectsDuplicateSerial() {
        when(shipmentItemRepository.existsBySerialNumber("SN-001")).thenReturn(true);

        assertThatThrownBy(() -> service.createShipment(manufacturer.getId(), createRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SN-001");
    }

    @Test
    void create_rejectsCourierAsDestinationParty() {
        CreateShipmentRequest req = new CreateShipmentRequest(
                "Factory A", "Store B", courier.getEmail(),
                List.of(new CreateShipmentRequest.Item("SN-002", "Phone", ProductCategory.ELECTRONICS)), null);

        assertThatThrownBy(() -> service.createShipment(manufacturer.getId(), req))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- assign courier ----------

    @Test
    void assignCourier_byOwningManufacturer_movesToAssigned() {
        Shipment s = shipmentInState(ShipmentStatus.CREATED);

        Shipment result = service.assignCourier(manufacturer.getId(), s.getId(), courier.getEmail());

        assertThat(result.getStatus()).isEqualTo(ShipmentStatus.ASSIGNED);
        assertThat(result.getCurrentCourier()).isEqualTo(courier);
    }

    @Test
    void assignCourier_rejectsNonCourierAssignee() {
        Shipment s = shipmentInState(ShipmentStatus.CREATED);

        assertThatThrownBy(() -> service.assignCourier(manufacturer.getId(), s.getId(), retailer.getEmail()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void assignCourier_byUninvolvedRetailer_isForbidden() {
        Shipment s = shipmentInState(ShipmentStatus.CREATED);

        assertThatThrownBy(() -> service.assignCourier(retailer.getId(), s.getId(), courier.getEmail()))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void assignCourier_onDeliveredShipment_isIllegalTransition() {
        Shipment s = shipmentInState(ShipmentStatus.DELIVERED);

        assertThatThrownBy(() -> service.assignCourier(manufacturer.getId(), s.getId(), courier.getEmail()))
                .isInstanceOf(IllegalStateTransitionException.class);
    }

    // ---------- transit updates ----------

    @Test
    void transit_onlyAssignedCourierMayUpdate() {
        Shipment s = shipmentInState(ShipmentStatus.ASSIGNED);
        s.setCurrentCourier(courier);
        User otherCourier = user(Role.COURIER, "other@t.dev");

        assertThatThrownBy(() -> service.updateTransitStatus(
                        otherCourier.getId(), s.getId(), transitRequest(ShipmentStatus.IN_TRANSIT)))
                .isInstanceOf(ForbiddenOperationException.class);

        Shipment updated = service.updateTransitStatus(
                courier.getId(), s.getId(), transitRequest(ShipmentStatus.IN_TRANSIT));
        assertThat(updated.getStatus()).isEqualTo(ShipmentStatus.IN_TRANSIT);
    }

    @Test
    void transit_cannotTargetDelivered() {
        Shipment s = shipmentInState(ShipmentStatus.IN_TRANSIT);
        s.setCurrentCourier(courier);

        assertThatThrownBy(() -> service.updateTransitStatus(
                        courier.getId(), s.getId(), transitRequest(ShipmentStatus.DELIVERED)))
                .isInstanceOf(IllegalStateTransitionException.class);
    }

    @Test
    void transit_frozenShipment_blocksAllChanges() {
        Shipment s = shipmentInState(ShipmentStatus.IN_TRANSIT);
        s.setCurrentCourier(courier);
        s.setFrozen(true);

        assertThatThrownBy(() -> service.updateTransitStatus(
                        courier.getId(), s.getId(), transitRequest(ShipmentStatus.AT_CHECKPOINT)))
                .isInstanceOf(IllegalStateTransitionException.class)
                .hasMessageContaining("frozen");
    }

    // ---------- confirm handoff ----------

    @Test
    void confirmHandoff_byDeclaredDestination_delivers() {
        Shipment s = shipmentInState(ShipmentStatus.AT_CHECKPOINT);
        s.setDestinationParty(retailer);

        Shipment result = service.confirmHandoff(retailer.getId(), s.getId());

        assertThat(result.getStatus()).isEqualTo(ShipmentStatus.DELIVERED);
    }

    @Test
    void confirmHandoff_byWrongParty_isForbidden() {
        Shipment s = shipmentInState(ShipmentStatus.AT_CHECKPOINT);
        s.setDestinationParty(retailer);

        assertThatThrownBy(() -> service.confirmHandoff(distributor.getId(), s.getId()))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void confirmHandoff_withoutDeclaredParty_recordsTheReceiver() {
        Shipment s = shipmentInState(ShipmentStatus.IN_TRANSIT);

        Shipment result = service.confirmHandoff(distributor.getId(), s.getId());

        assertThat(result.getStatus()).isEqualTo(ShipmentStatus.DELIVERED);
        assertThat(result.getDestinationParty()).isEqualTo(distributor);
    }

    @Test
    void confirmHandoff_fromCreated_isIllegal() {
        Shipment s = shipmentInState(ShipmentStatus.CREATED);
        s.setDestinationParty(retailer);

        assertThatThrownBy(() -> service.confirmHandoff(retailer.getId(), s.getId()))
                .isInstanceOf(IllegalStateTransitionException.class);
    }

    // ---------- dispute ----------

    @Test
    void dispute_byManufacturer_afterDelivery_works() {
        Shipment s = shipmentInState(ShipmentStatus.DELIVERED);

        Shipment result = service.dispute(manufacturer.getId(), s.getId());

        assertThat(result.getStatus()).isEqualTo(ShipmentStatus.DISPUTED);
    }

    @Test
    void dispute_byUninvolvedParty_isForbidden() {
        Shipment s = shipmentInState(ShipmentStatus.DELIVERED);

        assertThatThrownBy(() -> service.dispute(courier.getId(), s.getId()))
                .isInstanceOf(ForbiddenOperationException.class);
    }
}
