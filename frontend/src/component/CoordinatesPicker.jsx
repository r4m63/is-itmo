'use client';

import {useEffect, useMemo, useRef, useState} from "react";
import {Input} from "@heroui/react";
import {API_BASE} from "../../cfg.js";

export default function CoordinatesPicker({
                                              value,
                                              onChange,
                                              required = true,
                                              errorText = "Выберите координаты из списка"
                                          }) {
    const formatLabel = (it) =>
        it && (it.x !== undefined) && (it.y !== undefined)
            ? `${it.x}, ${it.y}`
            : "";

    const [query, setQuery] = useState(value?.id ? formatLabel(value) : "");
    const [items, setItems] = useState([]);
    const [loading, setLoading] = useState(false);
    const [selectedId, setSelectedId] = useState(value?.id || null);
    const [touched, setTouched] = useState(false);
    const debounceRef = useRef(null);
    const listId = useMemo(() => "coordinates-list-" + Math.random().toString(36).slice(2), []);

    useEffect(() => {
        if (value?.id) {
            setQuery(formatLabel(value));
            setSelectedId(value.id);
        } else {
            setQuery("");
            setSelectedId(null);
        }
    }, [value?.id, value?.x, value?.y]);

    const fetchCoordinates = async (q) => {
        setLoading(true);
        try {
            const url = new URL(`${API_BASE}/api/coordinates/search`);
            if (q) url.searchParams.set("q", q);
            url.searchParams.set("limit", "20");
            const res = await fetch(url, {credentials: "include", headers: {"Accept": "application/json"}});
            const data = await res.json().catch(() => []);
            setItems(Array.isArray(data) ? data : []);
        } catch {
            setItems([]);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => { fetchCoordinates(""); }, []);

    useEffect(() => {
        clearTimeout(debounceRef.current);
        debounceRef.current = setTimeout(() => fetchCoordinates(query.trim()), 300);
        return () => clearTimeout(debounceRef.current);
    }, [query]);

    const commitByLabel = (label) => {
        const hit = items.find(i => formatLabel(i) === label);
        setTouched(true);
        if (hit) {
            setSelectedId(hit.id);
            onChange?.({id: hit.id, x: hit.x, y: hit.y});
        } else {
            setSelectedId(null);
            onChange?.({id: null, x: null, y: null});
        }
    };

    const isInvalid = required && touched && !selectedId;

    return (
        <div className="flex flex-col gap-1">
            <Input
                list={listId}
                label="Координаты (начните вводить: “x, y” или число)"
                variant="bordered"
                value={query}
                isRequired={required}
                isInvalid={isInvalid}
                errorMessage={isInvalid ? errorText : undefined}
                description={
                    selectedId
                        ? `Выбран ID: ${selectedId}`
                        : (loading ? "Загрузка..." : "Выберите координаты из подсказок")
                }
                onChange={(e) => {
                    setQuery(e.target.value);
                    setSelectedId(null);
                }}
                onBlur={(e) => commitByLabel(e.target.value)}
            />
            <datalist id={listId}>
                {items.map(c => (
                    <option key={c.id} value={formatLabel(c)}>
                        {`id ${c.id}`}
                    </option>
                ))}
            </datalist>

            <input type="hidden" name="coordinatesId" value={selectedId || ""} required={required} />
        </div>
    );
}
