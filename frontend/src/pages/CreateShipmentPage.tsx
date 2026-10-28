import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "../api/client";
import { ErrorBanner } from "../components/RequestState";

export function CreateShipmentPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const hubsQuery = useQuery({ queryKey: ["hubs"], queryFn: api.listHubs });

  const [senderAddress, setSenderAddress] = useState("");
  const [receiverAddress, setReceiverAddress] = useState("");
  const [originHubId, setOriginHubId] = useState("");
  const [destinationHubId, setDestinationHubId] = useState("");

  const mutation = useMutation({
    mutationFn: () =>
      api.createShipment({
        senderAddress,
        receiverAddress,
        originHubId: originHubId ? Number(originHubId) : undefined,
        destinationHubId: destinationHubId ? Number(destinationHubId) : undefined,
      }),
    onSuccess: (shipment) => {
      queryClient.invalidateQueries({ queryKey: ["shipments"] });
      navigate(`/app/shipments/${shipment.id}`);
    },
  });

  function handleSubmit(e: FormEvent) {
    e.preventDefault();
    mutation.mutate();
  }

  const hubs = hubsQuery.data ?? [];

  return (
    <>
      <div className="page-header">
        <div>
          <h1>New shipment</h1>
          <p className="subtitle">Origin and destination are optional, but required for ETA and live tracking.</p>
        </div>
      </div>

      <div className="card" style={{ maxWidth: 560 }}>
        {mutation.isError && <ErrorBanner error={mutation.error} />}
        <form onSubmit={handleSubmit}>
          <div className="field">
            <label htmlFor="sender">Sender address</label>
            <input
              id="sender"
              required
              value={senderAddress}
              onChange={(e) => setSenderAddress(e.target.value)}
              placeholder="12 MG Road, Bengaluru"
            />
          </div>

          <div className="field">
            <label htmlFor="receiver">Receiver address</label>
            <input
              id="receiver"
              required
              value={receiverAddress}
              onChange={(e) => setReceiverAddress(e.target.value)}
              placeholder="5 Park St, Kolkata"
            />
          </div>

          <div className="form-row">
            <div className="field">
              <label htmlFor="origin">Origin hub</label>
              <select id="origin" value={originHubId} onChange={(e) => setOriginHubId(e.target.value)}>
                <option value="">None</option>
                {hubs.map((h) => (
                  <option key={h.id} value={h.id}>
                    {h.name}
                  </option>
                ))}
              </select>
            </div>
            <div className="field">
              <label htmlFor="destination">Destination hub</label>
              <select
                id="destination"
                value={destinationHubId}
                onChange={(e) => setDestinationHubId(e.target.value)}
              >
                <option value="">None</option>
                {hubs.map((h) => (
                  <option key={h.id} value={h.id}>
                    {h.name}
                  </option>
                ))}
              </select>
            </div>
          </div>

          <button type="submit" className="btn btn-primary" disabled={mutation.isPending}>
            {mutation.isPending ? "Creating…" : "Create shipment"}
          </button>
        </form>
      </div>
    </>
  );
}
