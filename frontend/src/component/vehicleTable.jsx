'use client';

import React, {useCallback, useMemo, useRef} from "react";
import {AgGridReact} from "ag-grid-react";
import {AllCommunityModule, colorSchemeDark, iconSetMaterial, ModuleRegistry, themeQuartz} from "ag-grid-community";
import {API_BASE} from "../../cfg.js";

ModuleRegistry.registerModules([AllCommunityModule]);

export const tableTheme = themeQuartz
    .withPart(iconSetMaterial)
    .withPart(colorSchemeDark)
    .withParams({
        backgroundColor: "#0f172a",
        foregroundColor: "#e5e7eb",
        headerBackgroundColor: "#111827",
        headerTextColor: "#e5e7eb",
        oddRowBackgroundColor: "#0b1326",
        accentColor: "#60a5fa",
        headerColumnResizeHandleColor: "#60a5fa",
        borderColor: "#1e293b",
        rowHoverColor: "#1f2937",
        selectedRowBackgroundColor: "#1e3a8a",
    });

export default function VehicleTable({onOpenEditVehicleModal, onReadyRefresh, onReadyControls}) {
    const gridApiRef = useRef(null);

    const colDefs = useMemo(() => ([
        {
            headerName: "ID",
            field: "id",
            colId: "id",
            width: 90,
            sortable: true,
            filter: "agNumberColumnFilter",
            floatingFilter: true
        },
        {
            headerName: "Edit",
            filter: false,
            floatingFilter: false,
            sortable: false,
            width: 90,
            cellRenderer: (p) => (
                <button
                    style={{
                        paddingInline: 15,
                        border: "none",
                        background: "#007bff",
                        color: "#fff",
                        borderRadius: 5,
                        cursor: "pointer"
                    }}
                    onClick={() => onOpenEditVehicleModal?.(p.data)}
                >
                    Edit
                </button>
            ),
        },
        {
            headerName: "Name",
            field: "name",
            colId: "name",
            sortable: true,
            filter: "agTextColumnFilter",
            floatingFilter: true
        },
        {
            headerName: "Type",
            field: "type",
            colId: "type",
            width: 140,
            sortable: true,
            filter: "agTextColumnFilter",
            floatingFilter: true
        },
        {
            headerName: "Fuel Type",
            field: "fuelType",
            colId: "fuelType",
            width: 140,
            sortable: true,
            filter: "agTextColumnFilter",
            floatingFilter: true
        },
        {
            headerName: "Created",
            colId: "creationTime",
            valueGetter: (p) => p.data?.creationDate ?? p.data?.creationTime ?? null,
            width: 190,
            sortable: true,
            filter: "agDateColumnFilter",
            floatingFilter: true,
            valueFormatter: (p) => {
                const v = p.value;
                if (!v) return "";
                const d = typeof v === "string" ? new Date(v) : v;
                return Number.isFinite(d?.getTime?.()) ? d.toLocaleString() : String(v);
            },
        },
        {
            headerName: "Coordinates",
            children: [
                {
                    headerName: "X",
                    colId: "coordinates.x",
                    valueGetter: (p) =>
                        p.data?.coordinates?.x ?? p.data?.coordinatesX ?? null,
                    width: 110,
                    sortable: true,
                    filter: "agNumberColumnFilter",
                    floatingFilter: true
                },
                {
                    headerName: "Y",
                    colId: "coordinates.y",
                    valueGetter: (p) =>
                        p.data?.coordinates?.y ?? p.data?.coordinatesY ?? null,
                    width: 110,
                    sortable: true,
                    filter: "agNumberColumnFilter",
                    floatingFilter: true
                },
            ],
        },
        {
            headerName: "Engine Power",
            field: "enginePower",
            colId: "enginePower",
            width: 150,
            sortable: true,
            filter: "agNumberColumnFilter",
            floatingFilter: true
        },
        {
            headerName: "Wheels",
            field: "numberOfWheels",
            colId: "numberOfWheels",
            width: 120,
            sortable: true,
            filter: "agNumberColumnFilter",
            floatingFilter: true
        },
        {
            headerName: "Capacity",
            field: "capacity",
            colId: "capacity",
            width: 120,
            sortable: true,
            filter: "agNumberColumnFilter",
            floatingFilter: true
        },
        {
            headerName: "Distance Travelled",
            field: "distanceTravelled",
            colId: "distanceTravelled",
            width: 170,
            sortable: true,
            filter: "agNumberColumnFilter",
            floatingFilter: true
        },
        {
            headerName: "Fuel Consumption",
            field: "fuelConsumption",
            colId: "fuelConsumption",
            width: 160,
            sortable: true,
            filter: "agNumberColumnFilter",
            floatingFilter: true
        },
    ]), [onOpenEditVehicleModal]);

    const mapSortModel = (sm = []) => sm.map(s => ({colId: s.colId, sort: s.sort}));

    const makeDatasource = useCallback(() => ({
        getRows: async (params) => {
            try {
                const body = {
                    startRow: params.startRow,
                    endRow: params.endRow,
                    sortModel: mapSortModel(params.sortModel),
                    filterModel: params.filterModel || {},
                };

                const res = await fetch(`${API_BASE}/api/vehicle/query`, {
                    method: "POST",
                    credentials: "include",
                    headers: {"Content-Type": "application/json", "Accept": "application/json"},
                    body: JSON.stringify(body),
                });

                if (!res.ok) {
                    params.failCallback();
                    return;
                }
                const data = await res.json();
                params.successCallback(data.rows || [], data.lastRow ?? 0);
            } catch (e) {
                console.error(e);
                params.failCallback();
            }
        }
    }), []);

    const setDatasource = useCallback(() => {
        if (!gridApiRef.current) return;
        const ds = makeDatasource();
        gridApiRef.current.setGridOption('datasource', ds);
        gridApiRef.current.purgeInfiniteCache();
    }, [makeDatasource]);

    const exposeRefresh = useCallback(() => {
        onReadyRefresh?.(() => {
            if (!gridApiRef.current) return;
            gridApiRef.current.refreshInfiniteCache();
        });
    }, [onReadyRefresh]);

    const exposeControls = useCallback(() => {
        if (!onReadyControls) return;
        const api = gridApiRef.current;
        if (!api) return;

        const clearSort = () => {
            api.applyColumnState?.({ defaultState: { sort: null, sortIndex: null } });
            api.setGridOption?.('sortModel', null);
        };

        const setAndGo = (filterModel) => {
            api.setFilterModel(filterModel || null);
            api.onFilterChanged();
            api.purgeInfiniteCache();
            api.ensureIndexVisible(0);
        };

        onReadyControls({
            refresh: () => api.refreshInfiniteCache(),
            clearFilters: () => {
                api.setFilterModel(null);
                api.onFilterChanged();
                clearSort();
                api.purgeInfiniteCache();
                api.ensureIndexVisible(0);
            },

            applyFilterFuelGt: (raw) => {
                const x = Number.parseFloat(raw);
                if (!Number.isFinite(x)) return;
                setAndGo({
                    fuelConsumption: { filterType: "number", type: "greaterThan", filter: x },
                });
            },
            applyFilterByType: (type) => {
                if (!type) return;
                setAndGo({
                    type: { filterType: "text", type: "equals", filter: String(type) },
                });
            },
            applyFilterEnginePowerRange: (rawMin, rawMax) => {
                const min = Number.parseFloat(rawMin);
                const max = Number.parseFloat(rawMax);
                if (!Number.isFinite(min) || !Number.isFinite(max) || min > max) return;
                setAndGo({
                    enginePower: { filterType: "number", type: "inRange", filter: min, filterTo: max },
                });
            },
            applyFilterByCoordinatesX: (xVal) => {
                if (xVal === undefined || xVal === null || xVal === "") return;
                setAndGo({
                    "coordinates.x": { filterType: "number", type: "equals", filter: Number(xVal) },
                });
            },
            applyFilterByCoordinatesY: (yVal) => {
                if (yVal === undefined || yVal === null || yVal === "") return;
                setAndGo({
                    "coordinates.y": { filterType: "number", type: "equals", filter: Number(yVal) },
                });
            },
        });
    }, [onReadyControls]);


    const onGridReady = useCallback((e) => {
        gridApiRef.current = e.api;
        setDatasource();
        exposeRefresh();
        exposeControls();
    }, [setDatasource, exposeRefresh, exposeControls]);

    const onFilterChanged = useCallback(() => {
        gridApiRef.current?.purgeInfiniteCache();
        gridApiRef.current?.ensureIndexVisible(0);
    }, []);

    const onSortChanged = useCallback(() => {
        gridApiRef.current?.purgeInfiniteCache();
        gridApiRef.current?.ensureIndexVisible(0);
    }, []);

    return (
        <div style={{minWidth: "100%", height: 600}}>
            <AgGridReact
                theme={tableTheme}
                columnDefs={colDefs}
                rowModelType="infinite"
                cacheBlockSize={50}
                maxBlocksInCache={2}
                pagination
                paginationPageSize={50}
                onGridReady={onGridReady}
                onFilterChanged={onFilterChanged}
                onSortChanged={onSortChanged}
                suppressMultiSort={false}
                defaultColDef={{filter: true, sortable: true, floatingFilter: true}}
            />
        </div>
    );
}
