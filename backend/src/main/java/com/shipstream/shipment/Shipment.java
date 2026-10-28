package com.shipstream.shipment;

import com.shipstream.common.InvalidTransitionException;
import com.shipstream.hub.Hub;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "shipments")
@Getter
@NoArgsConstructor
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tracking_id", nullable = false, unique = true, updatable = false)
    private String trackingId;

    @Column(name = "sender_address", nullable = false)
    private String senderAddress;

    @Column(name = "receiver_address", nullable = false)
    private String receiverAddress;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShipmentStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_hub_id")
    private Hub currentHub;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_hub_id")
    private Hub destinationHub;

    /** Set/refreshed whenever the shipment starts moving towards {@link #destinationHub}; see EtaService. */
    @Column(name = "estimated_delivery_at")
    private Instant estimatedDeliveryAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    public Shipment(String trackingId, String senderAddress, String receiverAddress, Hub originHub,
            Hub destinationHub) {
        this.trackingId = trackingId;
        this.senderAddress = senderAddress;
        this.receiverAddress = receiverAddress;
        this.currentHub = originHub;
        this.destinationHub = destinationHub;
        this.status = ShipmentStatus.CREATED;
    }

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /** Applies the transition if valid, otherwise throws. The only mutator for {@link #status}. */
    public void transitionTo(ShipmentStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new InvalidTransitionException(
                    "Cannot transition shipment " + trackingId + " from " + status + " to " + next);
        }
        this.status = next;
    }

    public void moveToHub(Hub hub) {
        this.currentHub = hub;
    }

    public void setEstimatedDeliveryAt(Instant eta) {
        this.estimatedDeliveryAt = eta;
    }
}
