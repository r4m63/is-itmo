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
    // как отображать запись в инпуте и в подсказках
    // "x, y" Если данных нет - пустая строка.
    const formatLabel = (it) =>
        it && (it.x !== undefined) && (it.y !== undefined)
            ? `${it.x}, ${it.y}`
            : "";

    // query - строка в поле ввода. Если во входном value есть id, то сразу подставляем "x, y". Иначе - пусто (приглашаем к вводу).
    const [query, setQuery] = useState(value?.id ? formatLabel(value) : "");
    const [items, setItems] = useState([]); // подсказки, которые получили с сервера
    const [loading, setLoading] = useState(false);
    const [selectedId, setSelectedId] = useState(value?.id || null); // id выбранной записи из подсказок
    const [touched, setTouched] = useState(false); // был ли фокус (используется для показа ошибки: required && touched && !selectedId)
    const debounceRef = useRef(null); // id таймера для дебаланса запросов
    const listId = useMemo(() => "coordinates-list-" + Math.random().toString(36).slice(2), []); // listId - уникальный id для привязки List к dataset

    // Синхронизация локального стейта с внешним пропом value
    useEffect(() => {
        if (value?.id) {
            setQuery(formatLabel(value));
            setSelectedId(value.id);
        } else {
            setQuery("");
            setSelectedId(null);
        }
    }, [value?.id, value?.x, value?.y]);

    // запрос к серверу за подсказками
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

    // При первом маунте загружаем первоначальные подсказки
    useEffect(() => {
        fetchCoordinates("");
    }, []);

    /**
     * Дебаунс обновления подсказок при вводе:
     * - чистим предыдущий таймер,
     * - ставим новый на 300мс,
     * - по истечении таймера дергаем fetchCoordinates(query.trim()).
     */
    useEffect(() => {
        clearTimeout(debounceRef.current);
        debounceRef.current = setTimeout(() => fetchCoordinates(query.trim()), 300);
        return () => clearTimeout(debounceRef.current);
    }, [query]);

    /**
     * commitByLabel - попытка “закоммитить” выбор по текущей строке в инпуте.
     * Мы ищем в items запись, чей label (formatLabel(i) -- "x, y") совпадает с тем, что вписано.
     * Если нашли - это валидный выбор: фиксируем selectedId и пробрасываем onChange({id, x, y}).
     * Если нет - сбрасываем выбор и пробрасываем onChange({id:null, x:null, y:null}).
     * touched ставим в true, чтобы при required включилась подсветка ошибки.
     */
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

    /**
     * isInvalid — критерий ошибки:
     * - поле отмечено как required,
     * - пользователь уже “трогал” поле (touched),
     * - нет выбранного id (selectedId пустой).
     */
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

            <input type="hidden" name="coordinatesId" value={selectedId || ""} required={required}/>
        </div>
    );
}
