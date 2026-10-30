import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState, type FormEvent } from "react";
import { api } from "../api/client";
import { EmptyState, ErrorBanner, Loading } from "../components/RequestState";

export function HubsPage() {
  const queryClient = useQueryClient();
  const hubsQuery = useQuery({ queryKey: ["hubs"], queryFn: api.listHubs });
  const routesQuery = useQuery({ queryKey: ["routes"], queryFn: api.listRoutes });

  const hubs = hubsQuery.data ?? [];
  const hubName = (id: number) => hubs.find((h) => h.id === id)?.name ?? `#${id}`;

  const deleteHub = useMutation({
    mutationFn: (id: number) => api.deleteHub(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["hubs"] });
      queryClient.invalidateQueries({ queryKey: ["routes"] }); // the hub's own routes went with it
    },
  });

  const deleteRoute = useMutation({
    mutationFn: (id: number) => api.deleteRoute(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["routes"] }),
  });

  const [hubForm, setHubForm] = useState({ name: "", location: "", capacity: "", latitude: "", longitude: "" });
  const createHub = useMutation({
    mutationFn: () =>
      api.createHub({
        name: hubForm.name,
        location: hubForm.location,
        capacity: Number(hubForm.capacity),
        latitude: Number(hubForm.latitude),
        longitude: Number(hubForm.longitude),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["hubs"] });
      setHubForm({ name: "", location: "", capacity: "", latitude: "", longitude: "" });
    },
  });

  const [routeForm, setRouteForm] = useState({ sourceHubId: "", destinationHubId: "", distanceKm: "" });
  const createRoute = useMutation({
    mutationFn: () =>
      api.createRoute({
        sourceHubId: Number(routeForm.sourceHubId),
        destinationHubId: Number(routeForm.destinationHubId),
        distanceKm: Number(routeForm.distanceKm),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["routes"] });
      setRouteForm({ sourceHubId: "", destinationHubId: "", distanceKm: "" });
    },
  });

  return (
    <>
      <div className="page-header">
        <div>
          <h1>Hubs & routes</h1>
          <p className="subtitle">The network shipments move through. Routes need both endpoints to exist first.</p>
        </div>
      </div>

      <div className="form-row" style={{ alignItems: "start" }}>
        <section>
          <h2 style={{ marginBottom: "var(--sp-3)" }}>Hubs</h2>
          {hubsQuery.isLoading && <Loading />}
          {hubsQuery.isError && <ErrorBanner error={hubsQuery.error} />}
          {deleteHub.isError && <ErrorBanner error={deleteHub.error} />}
          {hubs.length === 0 && !hubsQuery.isLoading ? (
            <EmptyState>No hubs yet.</EmptyState>
          ) : (
            <div className="table-card" style={{ marginBottom: "var(--sp-6)" }}>
              <table>
                <thead>
                  <tr>
                    <th>Name</th>
                    <th>Location</th>
                    <th>Capacity</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  {hubs.map((h) => (
                    <tr key={h.id}>
                      <td>{h.name}</td>
                      <td>{h.location}</td>
                      <td>{h.capacity}</td>
                      <td>
                        <button
                          className="btn btn-sm btn-danger"
                          disabled={deleteHub.isPending}
                          onClick={() => {
                            if (confirm(`Delete hub "${h.name}"? Its routes go with it.`)) {
                              deleteHub.mutate(h.id);
                            }
                          }}
                        >
                          Delete
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          <div className="card">
            <h3 style={{ marginBottom: "var(--sp-4)" }}>Add a hub</h3>
            {createHub.isError && <ErrorBanner error={createHub.error} />}
            <form
              onSubmit={(e: FormEvent) => {
                e.preventDefault();
                createHub.mutate();
              }}
            >
              <div className="field">
                <label htmlFor="hub-name">Name</label>
                <input
                  id="hub-name"
                  required
                  value={hubForm.name}
                  onChange={(e) => setHubForm({ ...hubForm, name: e.target.value })}
                  placeholder="BLR-1"
                />
              </div>
              <div className="field">
                <label htmlFor="hub-location">Location</label>
                <input
                  id="hub-location"
                  required
                  value={hubForm.location}
                  onChange={(e) => setHubForm({ ...hubForm, location: e.target.value })}
                  placeholder="Bengaluru"
                />
              </div>
              <div className="form-row">
                <div className="field">
                  <label htmlFor="hub-capacity">Capacity</label>
                  <input
                    id="hub-capacity"
                    type="number"
                    min={1}
                    required
                    value={hubForm.capacity}
                    onChange={(e) => setHubForm({ ...hubForm, capacity: e.target.value })}
                  />
                </div>
              </div>
              <div className="form-row">
                <div className="field">
                  <label htmlFor="hub-lat">Latitude</label>
                  <input
                    id="hub-lat"
                    type="number"
                    step="any"
                    required
                    value={hubForm.latitude}
                    onChange={(e) => setHubForm({ ...hubForm, latitude: e.target.value })}
                  />
                </div>
                <div className="field">
                  <label htmlFor="hub-lon">Longitude</label>
                  <input
                    id="hub-lon"
                    type="number"
                    step="any"
                    required
                    value={hubForm.longitude}
                    onChange={(e) => setHubForm({ ...hubForm, longitude: e.target.value })}
                  />
                </div>
              </div>
              <button type="submit" className="btn btn-primary" disabled={createHub.isPending}>
                {createHub.isPending ? "Adding…" : "Add hub"}
              </button>
            </form>
          </div>
        </section>

        <section>
          <h2 style={{ marginBottom: "var(--sp-3)" }}>Routes</h2>
          {routesQuery.isLoading && <Loading />}
          {routesQuery.isError && <ErrorBanner error={routesQuery.error} />}
          {deleteRoute.isError && <ErrorBanner error={deleteRoute.error} />}
          {(routesQuery.data ?? []).length === 0 && !routesQuery.isLoading ? (
            <EmptyState>No routes yet.</EmptyState>
          ) : (
            <div className="table-card" style={{ marginBottom: "var(--sp-6)" }}>
              <table>
                <thead>
                  <tr>
                    <th>From</th>
                    <th>To</th>
                    <th>Distance</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  {(routesQuery.data ?? []).map((r) => (
                    <tr key={r.id}>
                      <td>{hubName(r.sourceHubId)}</td>
                      <td>{hubName(r.destinationHubId)}</td>
                      <td>{r.distanceKm} km</td>
                      <td>
                        <button
                          className="btn btn-sm btn-danger"
                          disabled={deleteRoute.isPending}
                          onClick={() => deleteRoute.mutate(r.id)}
                        >
                          Delete
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          <div className="card">
            <h3 style={{ marginBottom: "var(--sp-4)" }}>Add a route</h3>
            {createRoute.isError && <ErrorBanner error={createRoute.error} />}
            <form
              onSubmit={(e: FormEvent) => {
                e.preventDefault();
                createRoute.mutate();
              }}
            >
              <div className="field">
                <label htmlFor="route-source">Source hub</label>
                <select
                  id="route-source"
                  required
                  value={routeForm.sourceHubId}
                  onChange={(e) => setRouteForm({ ...routeForm, sourceHubId: e.target.value })}
                >
                  <option value="" disabled>
                    Select…
                  </option>
                  {hubs.map((h) => (
                    <option key={h.id} value={h.id}>
                      {h.name}
                    </option>
                  ))}
                </select>
              </div>
              <div className="field">
                <label htmlFor="route-dest">Destination hub</label>
                <select
                  id="route-dest"
                  required
                  value={routeForm.destinationHubId}
                  onChange={(e) => setRouteForm({ ...routeForm, destinationHubId: e.target.value })}
                >
                  <option value="" disabled>
                    Select…
                  </option>
                  {hubs.map((h) => (
                    <option key={h.id} value={h.id}>
                      {h.name}
                    </option>
                  ))}
                </select>
              </div>
              <div className="field">
                <label htmlFor="route-distance">Distance (km)</label>
                <input
                  id="route-distance"
                  type="number"
                  min={1}
                  required
                  value={routeForm.distanceKm}
                  onChange={(e) => setRouteForm({ ...routeForm, distanceKm: e.target.value })}
                />
              </div>
              <button type="submit" className="btn btn-primary" disabled={createRoute.isPending}>
                {createRoute.isPending ? "Adding…" : "Add route"}
              </button>
            </form>
          </div>
        </section>
      </div>
    </>
  );
}
