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

    const gridApiRef = useRef(null); // API таблицы
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

    /**
     * AG Grid отдаёт sortModel в виде массива объектов {colId, sort, sortIndex?...}
     * достаточно colId и sort ("asc"/"desc") для бэка
     */
    const mapSortModel = (sm = []) => sm.map(s => ({colId: s.colId, sort: s.sort}));


    const makeDatasource = useCallback(() => ({
        /**
         * params содержит:
         * - startRow/endRow - диапазон строк, которые сейчас нужны гриду
         * - sortModel - массив сортировок [{colId, sort}]
         * - filterModel - объект фильтров по колонкам
         *
         * отправляем POST на /api/vehicle/query с этими параметрами
         * В ответ ожидаем:
         *   { rows: [...], lastRow: number } - lastRow нужен AG Grid, чтобы понимать общий размер данных.
         */
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

    /**
     * Установка datasource в грид и сброс кэша.
     * В infinite-модели кэшируется несколько "блоков" данных; purgeInfiniteCache полностью его сбрасывает.
     */
    const setDatasource = useCallback(() => {
        if (!gridApiRef.current) return;
        const ds = makeDatasource();
        gridApiRef.current.setGridOption('datasource', ds); // новый источник данных
        gridApiRef.current.purgeInfiniteCache(); // полностью сбрасываем кэш
    }, [makeDatasource]);

    /**
     * exposeRefresh - отдаём родителю функцию, которая обновляет кэш данных (перезагрузка текущих блоков).
     * Родитель вызовет onReadyRefresh(fn) и сохранит этот fn у себя.
     */
    const exposeRefresh = useCallback(() => {
        onReadyRefresh?.(() => {
            if (!gridApiRef.current) return;
            gridApiRef.current.refreshInfiniteCache(); // “перечитать” уже запрошенные блоки заново
        });
    }, [onReadyRefresh]);

    /**
     * exposeControls - отдаём родителю набор методов для управления фильтрами таблицы извне.
     * Эти методы вызывают AG Grid API: устанавливают filterModel, чистят сортировку,
     * сбрасывают кэш и скроллят на начало.
     */
    const exposeControls = useCallback(() => {
        if (!onReadyControls) return;
        const api = gridApiRef.current;
        if (!api) return;

        // хелпер: почистить сортировку у всех колонок
        const clearSort = () => {
            api.applyColumnState?.({defaultState: {sort: null, sortIndex: null}});
            api.setGridOption?.('sortModel', null);
        };

        // хелпер: применить фильтры, сбросить кэш, пролистать на первую строку
        const setAndGo = (filterModel) => {
            api.setFilterModel(filterModel || null);
            api.onFilterChanged();
            api.purgeInfiniteCache();
            api.ensureIndexVisible(0);
        };

        // формирование объектов контролов и отдаём наверх
        onReadyControls({
            // Принудительное обновление текущего кэша (без смены фильтров)
            refresh: () => api.refreshInfiniteCache(),

            // Полный сброс фильтров+сортировки и сброс кэша
            clearFilters: () => {
                api.setFilterModel(null);
                api.onFilterChanged();
                clearSort();
                api.purgeInfiniteCache();
                api.ensureIndexVisible(0);
            },

            // Применить фильтр: fuelConsumption > X
            applyFilterFuelGt: (raw) => {
                const x = Number.parseFloat(raw);
                if (!Number.isFinite(x)) return;
                setAndGo({
                    fuelConsumption: {filterType: "number", type: "greaterThan", filter: x},
                });
            },

            // Применить фильтр: type == выбранному значению
            applyFilterByType: (type) => {
                if (!type) return;
                setAndGo({
                    type: {filterType: "text", type: "equals", filter: String(type)},
                });
            },

            // Применить фильтр: enginePower в диапазоне [min, max]
            applyFilterEnginePowerRange: (rawMin, rawMax) => {
                const min = Number.parseFloat(rawMin);
                const max = Number.parseFloat(rawMax);
                if (!Number.isFinite(min) || !Number.isFinite(max) || min > max) return;
                setAndGo({
                    enginePower: {filterType: "number", type: "inRange", filter: min, filterTo: max},
                });
            },

            // Применить фильтр по координате X (equals)
            applyFilterByCoordinatesX: (xVal) => {
                if (xVal === undefined || xVal === null || xVal === "") return;
                setAndGo({
                    "coordinates.x": {filterType: "number", type: "equals", filter: Number(xVal)},
                });
            },

            // Применить фильтр по координате Y (equals)
            applyFilterByCoordinatesY: (yVal) => {
                if (yVal === undefined || yVal === null || yVal === "") return;
                setAndGo({
                    "coordinates.y": {filterType: "number", type: "equals", filter: Number(yVal)},
                });
            },
        });
    }, [onReadyControls]);


    /**
     * onGridReady — вызывается один раз после инициализации таблицы.
     * Здесь мы сохраняем API грида, выставляем datasource и пробрасываем наружу control-функции.
     */
    const onGridReady = useCallback((e) => {
        gridApiRef.current = e.api; // запоминаем ссылку на API грида
        setDatasource();            // подключаем источник данных
        exposeRefresh();            // отдаём наружу функцию refresh
        exposeControls();           // отдаём наружу контролы фильтров
    }, [setDatasource, exposeRefresh, exposeControls]);

    /**
     * События, которые приходят от AG Grid при смене фильтров/сортировок самим пользователем в UI.
     * Мы полностью сбрасываем кэш (purgeInfiniteCache) и пролистываем в начало (ensureIndexVisible(0)),
     * чтобы запросы в бэкенд соответствовали новым условиям.
     */
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
