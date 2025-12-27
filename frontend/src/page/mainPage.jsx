'use client';

import {useCallback, useEffect, useMemo, useRef, useState} from "react";
import {
    Button,
    Input,
    Modal,
    ModalBody,
    ModalContent,
    ModalFooter,
    ModalHeader,
    Select,
    SelectItem,
    useDisclosure
} from "@heroui/react";
import {toast} from "sonner";
import styles from "./mainPage.module.css";
import {useNavigate} from "react-router-dom";
// import useAuthStore from "../store/auth.js";
import {API_BASE} from "../../cfg.js";
import VehicleTable from "../component/vehicleTable.jsx";
import CoordinatesPicker from "../component/CoordinatesPicker.jsx";

const VEHICLE_TYPES = ["CAR", "HELICOPTER", "MOTORCYCLE", "CHOPPER"];
const FUEL_TYPES = ["KEROSENE", "MANPOWER", "NUCLEAR"];

export default function MainPage() {
    const navigate = useNavigate();
    // const {setIsAuthed} = useAuthStore();

    // null - новое, иначе - редактирование
    const [activeVehicle, setActiveVehicle] = useState(null);

    const [name, setName] = useState("");
    const [type, setType] = useState("");
    const [enginePower, setEnginePower] = useState("");
    const [numberOfWheels, setNumberOfWheels] = useState("");
    const [capacity, setCapacity] = useState("");
    const [distanceTravelled, setDistanceTravelled] = useState("");
    const [fuelConsumption, setFuelConsumption] = useState("");
    const [fuelType, setFuelType] = useState("");

    const [coordinatesId, setCoordinatesId] = useState(null); // id
    const [coordinatesObj, setCoordinatesObj] = useState(null); // объект x y из справочника

    const {isOpen, onOpen, onOpenChange} = useDisclosure();

    const [tableControls, setTableControls] = useState(null); // объект интерфейса управления фильтрами таблицы
    const [refreshGrid, setRefreshGrid] = useState(() => () => {
    }); // функция перезагрузки данных таблицы

    const wsRef = useRef(null); // текущее соединение WebSocket
    const reconnectTimerRef = useRef(null); // id таймера реконнекта

    // url
    const WS_URL = useMemo(() => {
        try {
            const u = new URL(API_BASE);
            const wsProto = u.protocol === "https:" ? "wss:" : "ws:";
            return `${wsProto}//${u.host}${u.pathname.replace(/\/+$/, '')}/ws/vehicles`;
        } catch {
            const loc = window.location;
            const wsProto = loc.protocol === "https:" ? "wss:" : "ws:";
            const base = API_BASE?.startsWith("/") ? API_BASE : `/${API_BASE || ""}`;
            return `${wsProto}//${loc.host}${base.replace(/\/+$/, '')}/ws/vehicles`;
        }
    }, []);

    const connectWs = useCallback(() => {
        if (wsRef.current?.readyState === WebSocket.OPEN || wsRef.current?.readyState === WebSocket.CONNECTING)
            return; // уже открыт или соединяется

        let retry = 1000; // ms
        const openSocket = () => {
            const ws = new WebSocket(WS_URL);
            wsRef.current = ws;

            ws.onopen = () => {
                retry = 1000; // Соединение установлено - сброс интервала реконнекта
            };
            ws.onmessage = (evt) => {
                const msg = (evt.data || "").toString().trim();
                if (msg === "refresh") refreshGrid?.();
            };
            ws.onclose = () => {
                reconnectTimerRef.current = setTimeout(() => {
                    retry = Math.min(retry * 2, 10000); // таймер реконнекта с увеличением интервала до 10 s
                    openSocket();
                }, retry);
            };
            ws.onerror = () => {
                try {
                    ws.close();
                } catch {
                }
            };
        };

        openSocket();
    }, [WS_URL, refreshGrid]);

    useEffect(() => {
        connectWs();
        return () => {
            clearTimeout(reconnectTimerRef.current);
            try {
                wsRef.current?.close();
            } catch {
            }
        };
    }, [connectWs]);

    const openNewVehicleModal = () => {
        setActiveVehicle(null);
        setName("");
        setType("");
        setEnginePower("");
        setNumberOfWheels("");
        setCapacity("");
        setDistanceTravelled("");
        setFuelConsumption("");
        setFuelType("");
        setCoordinatesId(null);
        setCoordinatesObj(null);
        onOpen();
    };

    const openEditVehicleModal = async (vehicle) => {
        setActiveVehicle(vehicle);
        setName(vehicle.name || "");
        setType(vehicle.type || "");
        setEnginePower(vehicle.enginePower ?? "");
        setNumberOfWheels(vehicle.numberOfWheels ?? "");
        setCapacity(vehicle.capacity ?? "");
        setDistanceTravelled(vehicle.distanceTravelled ?? "");
        setFuelConsumption(vehicle.fuelConsumption ?? "");
        setFuelType(vehicle.fuelType || "");

        // вложенные координаты
        const nested = vehicle.coordinates ?? null;
        const flatX = vehicle.coordinatesX ?? vehicle.coordX ?? null;
        const flatY = vehicle.coordinatesY ?? vehicle.coordY ?? null;
        const coordId = vehicle.coordinatesId ?? nested?.id ?? null;

        // сбор единого объекта координат
        let c = null;
        if (nested && (nested.x != null) && (nested.y != null)) {
            c = {id: coordId ?? nested.id ?? null, x: nested.x, y: nested.y};
        } else if (flatX != null && flatY != null) {
            c = {id: coordId ?? null, x: flatX, y: flatY};
        }

        setCoordinatesId(c?.id ?? null);
        setCoordinatesObj(c);

        onOpen();
    };

    function validate() {
        if (!name.trim()) return "Заполните name.";
        if (!type) return "Выберите type.";
        if (!numberOfWheels || Number(numberOfWheels) <= 0) return "numberOfWheels должно быть > 0.";
        if (!fuelConsumption || Number(fuelConsumption) <= 0) return "fuelConsumption должно быть > 0.";
        if (!fuelType) return "Выберите fuelType.";
        if (!coordinatesId) return "Выберите координаты (из справочника Coordinates).";

        if (enginePower !== "" && Number(enginePower) <= 0) return "enginePower должно быть > 0.";
        if (capacity !== "" && Number(capacity) <= 0) return "capacity должно быть > 0.";
        if (distanceTravelled !== "" && Number(distanceTravelled) <= 0) return "distanceTravelled должно быть > 0.";
        return null;
    }

    const handleSave = async () => {
        const err = validate();
        if (err)
            return toast.warning(err);

        const payload = {
            id: activeVehicle?.id ?? null,
            name: name.trim(),
            type,
            enginePower: enginePower === "" ? null : Number(enginePower),
            numberOfWheels: Number(numberOfWheels),
            capacity: capacity === "" ? null : Number(capacity),
            distanceTravelled: distanceTravelled === "" ? null : Number(distanceTravelled),
            fuelConsumption: Number(fuelConsumption),
            fuelType,
            coordinatesId: Number(coordinatesId),
        };

        const isEdit = Boolean(activeVehicle?.id);
        const url = isEdit
            ? `${API_BASE}/api/vehicle/${activeVehicle.id}`
            : `${API_BASE}/api/vehicle`;

        try {
            const res = await fetch(url, {
                method: isEdit ? "PUT" : "POST",
                credentials: "include",
                headers: {"Content-Type": "application/json"},
                body: JSON.stringify(payload),
            });
            if (res.ok) {
                refreshGrid();
                onOpenChange(false);
                toast.success("Сохранено");
            } else {
                const errorData = await res.json().catch(() => ({}));
                switch (res.status) {
                    // case 401:
                    //     setIsAuthed(false);
                    //     toast.error(errorData.message || 'Not correct credentials');
                    //     break;
                    default:
                        toast.error(errorData.message || `Error: ${res.status} - ${res.statusText}`);
                        break;
                }
            }
        } catch (e) {
            console.error(e);
            toast.error("Ошибка сохранения");
        }
    };

    const handleDelete = async () => {
        if (!activeVehicle?.id)
            return;

        try {
            const res = await fetch(`${API_BASE}/api/vehicle/${activeVehicle.id}`, {
                method: "DELETE",
                credentials: "include",
            });
            if (res.ok) {
                refreshGrid();
                onOpenChange(false);
                toast.success("Удалено");
                return;
            }
            let errorData = {};
            try {
                errorData = await res.json();
            } catch {
            }
            toast.error(errorData.message || `Error: ${res.status}`);
        } catch (e) {
            console.error(e);
            toast.error("Ошибка удаления");
        }
    };

    // const handleLogout = async () => {
    //     try {
    //         const res = await fetch(`${API_BASE}/api/auth/logout`, {
    //             method: "POST",
    //             credentials: "include",
    //         });
    //         if (!res.ok) throw new Error(`${res.status} ${await res.text()}`);
    //         setIsAuthed(false);
    //         navigate("/login", {replace: true});
    //         toast.success("Вы вышли из аккаунта");
    //     } catch (err) {
    //         toast.error("Не удалось выйти: " + (err.message || ""));
    //     }
    // };

    const {isOpen: isPresetOpen, onOpen: onPresetOpen, onOpenChange: onPresetOpenChange} = useDisclosure();

    const [presetFuelGtCount, setPresetFuelGtCount] = useState("");
    const [presetFuelGtList, setPresetFuelGtList] = useState("");

    const [presetType, setPresetType] = useState("");
    const [presetEngMin, setPresetEngMin] = useState("");
    const [presetEngMax, setPresetEngMax] = useState("");

    const presetMinDistance = async () => {
        try {
            const res = await fetch(`${API_BASE}/api/vehicle/special/min-distance`, {
                credentials: "include",
                headers: {"Accept": "application/json"},
            });
            if (res.status === 204) {
                toast.info("Нет объектов с заполненным distanceTravelled");
                return;
            }
            if (!res.ok) throw new Error(`${res.status}`);
            const dto = await res.json();
            onPresetOpenChange(false);
            await openEditVehicleModal(dto);
            toast.success("Загружен объект с минимальным пробегом");
        } catch (e) {
            console.error(e);
            toast.error("Не удалось получить объект");
        }
    };

    const presetCountFuelGt = async () => {
        const v = Number(presetFuelGtCount);
        if (isNaN(v) || v <= 0) return toast.warning("Введите корректное значение топлива > 0");
        try {
            const res = await fetch(`${API_BASE}/api/vehicle/special/count-fuel-gt?v=${encodeURIComponent(v)}`, {
                credentials: "include",
                headers: {"Accept": "application/json"},
            });
            if (!res.ok) throw new Error(`${res.status}`);
            const data = await res.json();
            toast.info(`Найдено: ${data.count}`);
        } catch (e) {
            console.error(e);
            toast.error("Не удалось выполнить подсчет");
        }
    };

    const presetListFuelGt = async () => {
        const v = Number(presetFuelGtList);
        if (!Number.isFinite(v) || v <= 0) return toast.warning("Введите корректное значение топлива > 0");
        try {
            const res = await fetch(`${API_BASE}/api/vehicle/special/list-fuel-gt?v=${encodeURIComponent(v)}`, {
                credentials: "include",
                headers: {"Accept": "application/json"},
            });
            if (!res.ok) throw new Error(`${res.status}`);
            tableControls?.applyFilterFuelGt(v);
            onPresetOpenChange(false);
            toast.success("Фильтр применён");
        } catch (e) {
            console.error(e);
            toast.error("Не удалось получить список");
        }
    };

    const presetListByType = async () => {
        if (!presetType) return toast.warning("Выберите тип");
        try {
            const res = await fetch(`${API_BASE}/api/vehicle/special/by-type?type=${encodeURIComponent(presetType)}`, {
                credentials: "include",
                headers: {"Accept": "application/json"},
            });
            if (!res.ok) throw new Error(`${res.status}`);
            tableControls?.applyFilterByType(presetType);
            onPresetOpenChange(false);
            toast.success("Фильтр применён");
        } catch (e) {
            console.error(e);
            toast.error("Не удалось получить по типу");
        }
    };

    const presetListByEngineRange = async () => {
        const min = Number(presetEngMin);
        const max = Number(presetEngMax);
        if (!Number.isFinite(min) || !Number.isFinite(max) || min > max || !min || !max) {
            return toast.warning("Введите корректный диапазон мощности");
        }
        try {
            const res = await fetch(`${API_BASE}/api/vehicle/special/by-engine-range?min=${min}&max=${max}`, {
                credentials: "include",
                headers: {"Accept": "application/json"},
            });
            if (!res.ok) throw new Error(`${res.status}`);
            tableControls?.applyFilterEnginePowerRange(min, max);
            onPresetOpenChange(false);
            toast.success("Фильтр применён");
        } catch (e) {
            console.error(e);
            toast.error("Не удалось получить по диапазону");
        }
    };

    const handleResetFilters = () => {
        tableControls?.clearFilters?.();
    };

    const fileInputRef = useRef(null);

    const handleImportClick = () => {
        fileInputRef.current?.click();
    };

    const extractMessageFromBody = (raw) => {
        if (!raw) return null;

        // попробуем JSON
        try {
            const obj = JSON.parse(raw);

            // твой формат: {message: "..."} или {errors:[...]}
            if (obj?.message) return String(obj.message);

            // иногда Jakarta/сервер может вернуть {error:"...", ...}
            if (obj?.error) return String(obj.error);

            return null;
        } catch {
            // если это не JSON — вернём как есть (обрежем)
            const s = String(raw).trim();
            if (!s) return null;
            return s.length > 400 ? s.slice(0, 400) + "…" : s;
        }
    };

    const handleFileChange = async (e) => {
        const file = e.target.files?.[0];
        if (!file) return;

        try {
            // Быстрая проверка формата перед отправкой
            try {
                const text = await file.text();
                const parsed = JSON.parse(text);
                if (!Array.isArray(parsed)) {
                    toast.warning("Ожидается JSON-массив объектов для импорта");
                    return;
                }
            } catch {
                toast.warning("Файл не является корректным JSON");
                return;
            }

            // запрос
            const response = await fetch(`${API_BASE}/api/vehicle/import`, {
                method: "POST",
                headers: {
                    "Content-Type": file.type || "application/json",
                    "X-Filename": file.name || "import.json",
                },
                credentials: "include",
                body: file,
            });

            if (response.ok) {
                refreshGrid();
                toast.success("Импорт выполнен");
                return;
            }

            // читаем тело как текст, потом пытаемся достать message
            const rawBody = await response.text().catch(() => "");
            const msgFromBody = extractMessageFromBody(rawBody);

            // 400: валидация (у тебя отдельная логика)
            if (response.status === 400) {
                // попробуем распарсить JSON, чтобы достать errors[]
                let errorData = {};
                try {
                    errorData = JSON.parse(rawBody || "{}");
                } catch {
                }

                if (Array.isArray(errorData.errors) && errorData.errors.length > 0) {
                    const lines = errorData.errors.map((err) => {
                        const rowPart = err.rowNumber != null ? `Элемент ${err.rowNumber}: ` : "";
                        return rowPart + (err.message ?? "Ошибка");
                    });

                    toast.error(
                        <div>
                            {lines.map((line, idx) => (
                                <div key={idx}>{line}</div>
                            ))}
                        </div>
                    );
                } else {
                    toast.error(errorData.message || msgFromBody || "Ошибка валидации (400)");
                }
                return;
            }

            // 500/503 — как раз “MinIO упал / серверная ошибка”
            if (response.status === 503) {
                toast.error(msgFromBody || "Сервис временно недоступен. Импорт откатился.");
                return;
            }

            if (response.status >= 500) {
                // На защите можешь специально показать эту фразу:
                toast.error(
                    msgFromBody ||
                    "Ошибка сервера: не удалось сохранить файл в хранилище (MinIO). Импорт откатился."
                );
                return;
            }

            toast.error(msgFromBody || `Ошибка: ${response.status} ${response.statusText}`);
        } catch (err) {
            console.error(err);

            const message = String(err?.message || "");

            // сетевые/соединение
            if (message.includes("Failed to fetch") || message.includes("NetworkError")) {
                toast.error("Нет соединения с сервером.");
                return;
            }

            if (message.toLowerCase().includes("timeout") || message.toLowerCase().includes("aborted")) {
                toast.error("Запрос слишком долго выполнялся и был прерван.");
                return;
            }

            toast.error("Не удалось выполнить импорт: сервер недоступен или произошла ошибка.");
        } finally {
            e.target.value = "";
        }
    };

    const [isHistoryOpen, setIsHistoryOpen] = useState(false);
    const [history, setHistory] = useState([]);
    const [historyLoading, setHistoryLoading] = useState(false);

    const openHistoryModal = async () => {
        setIsHistoryOpen(true);
        setHistoryLoading(true);
        try {
            const response = await fetch(`${API_BASE}/api/vehicle/import/history?limit=50`, {
                method: "GET",
                credentials: "include",
            });

            if (!response.ok) {
                toast.error(`Ошибка загрузки истории: ${response.status}`);
                return;
            }

            const data = await response.json();
            setHistory(data);
        } catch (err) {
            console.error(err);
            toast.error("Ошибка сети при загрузке истории");
        } finally {
            setHistoryLoading(false);
        }
    };

    return (
        <>
            <div className={styles.totalwrapp}>
                <div className={styles.top}>
                    <div className={styles.left}>
                        <h1 className={styles.title}>Таблица транспортных средств</h1>
                        <div className={styles.btnWrapper}>
                            <Button color="primary" className={styles.control} onPress={openNewVehicleModal}>
                                Добавить
                            </Button>
                            <Button color="primary" className={styles.control} onPress={onPresetOpen}>
                                Пресеты
                            </Button>
                            <Button color="warning" className={styles.control} onPress={handleResetFilters}>
                                Сбросить фильтры
                            </Button>
                            <Button color="primary" className={styles.control}
                                    onPress={() => navigate("/coordinates")}>
                                Coordinates
                            </Button>
                            <input
                                ref={fileInputRef}
                                type="file"
                                accept="application/json"
                                style={{display: "none"}}
                                onChange={handleFileChange}
                            />
                            <Button
                                color="primary"
                                className={styles.control}
                                onPress={handleImportClick}
                            >
                                Импорт JSON
                            </Button>
                            <Button
                                color="secondary"
                                className={styles.control}
                                onPress={openHistoryModal}
                            >
                                История
                            </Button>
                        </div>
                    </div>
                    {/* <div className={styles.right}>
                        <Popover placement="bottom-end" showArrow>
                            <PopoverTrigger>
                                <div className={styles.profileWrapp}>
                                    <img src="/user.png" alt="User avatar" width={40} height={40}
                                         className="rounded-full object-cover"/>
                                    <h1 className="ml-3 font-medium">Мой профиль</h1>
                                </div>
                            </PopoverTrigger>
                            <PopoverContent className="p-4 flex flex-col items-stretch gap-2">
                                <Button color="danger" onPress={handleLogout}>Выход</Button>
                            </PopoverContent>
                        </Popover>
                    </div> */}
                </div>
            </div>

            <VehicleTable
                onOpenEditVehicleModal={openEditVehicleModal}
                onReadyRefresh={(fn) => setRefreshGrid(() => fn)}
                onReadyControls={(controls) => setTableControls(controls)}
            />

            <Modal
                isOpen={isHistoryOpen}
                onOpenChange={setIsHistoryOpen}
                size="4xl"
                scrollBehavior="inside"
            >
                <ModalContent>
                    {onClose => (
                        <>
                            <ModalHeader className={styles.modalHeader}>
                                История импорта
                            </ModalHeader>
                            <ModalBody>
                                {historyLoading ? (
                                    <div>Загрузка...</div>
                                ) : history.length === 0 ? (
                                    <div>История пуста</div>
                                ) : (
                                    <div className={styles.historyTableWrapper}>
                                        <table className={styles.historyTable}>
                                            <thead>
                                            <tr>
                                                <th>ID операции</th>
                                                <th>Статус</th>
                                                <th>Добавлено объектов</th>
                                                <th>Файл</th>
                                                <th>Дата/время</th>
                                            </tr>
                                            </thead>
                                            <tbody>
                                            {history.map((item) => (
                                                <tr key={item.id}>
                                                    <td>{item.id}</td>
                                                    <td>{item.success ? "Успех" : "Ошибка"}</td>
                                                    <td>
                                                        {item.success
                                                            ? item.importedCount
                                                            : "-"}
                                                    </td>
                                                    <td>
                                                        {(item.fileDownloadUrl || item.success) ? (
                                                            <Button
                                                                size="sm"
                                                                variant="flat"
                                                                as="a"
                                                                href={`${API_BASE}${item.fileDownloadUrl || `/api/vehicle/import/history/${item.id}/file`}`}
                                                                target="_blank"
                                                                rel="noreferrer"
                                                            >
                                                                Скачать
                                                            </Button>
                                                        ) : (
                                                            "-"
                                                        )}
                                                    </td>
                                                    <td>
                                                        {item.creationTime
                                                            ? new Date(item.creationTime).toLocaleString()
                                                            : ""}
                                                    </td>
                                                </tr>
                                            ))}
                                            </tbody>
                                        </table>
                                    </div>
                                )}
                            </ModalBody>
                            <ModalFooter>
                                <Button variant="light" onPress={onClose}>
                                    Закрыть
                                </Button>
                            </ModalFooter>
                        </>
                    )}
                </ModalContent>
            </Modal>

            <Modal isOpen={isOpen} onOpenChange={onOpenChange} isDismissable={false}>
                <ModalContent className={styles.postModalBody}>
                    {(close) => (
                        <>
                            <ModalHeader>
                                {activeVehicle ? "Редактировать транспорт" : "Новый транспорт"}
                            </ModalHeader>

                            <ModalBody className={styles.postModalBody}>
                                <Input
                                    label="Название"
                                    variant="bordered"
                                    value={name}
                                    onChange={(e) => setName(e.target.value)}
                                    isRequired
                                />

                                <CoordinatesPicker
                                    value={coordinatesObj}
                                    onChange={(sel) => {
                                        if (sel?.id) {
                                            setCoordinatesId(sel.id);
                                            setCoordinatesObj(sel); // {id,x,y}
                                        } else {
                                            setCoordinatesId(null);
                                            setCoordinatesObj(null);
                                        }
                                    }}
                                    required={true}
                                    errorText="Выберите координаты из списка"
                                />

                                <Select
                                    label="Тип"
                                    variant="bordered"
                                    selectedKeys={type ? [type] : []}
                                    onChange={(e) => setType(e.target.value)}
                                    isRequired
                                >
                                    {VEHICLE_TYPES.map((t) => (
                                        <SelectItem key={t} value={t}>{t}</SelectItem>
                                    ))}
                                </Select>

                                <div className="grid grid-cols-2 gap-3">
                                    <Input type="number" label="Мощность двигателя" variant="bordered"
                                           value={enginePower} onChange={(e) => setEnginePower(e.target.value)}
                                           min={0}/>
                                    <Input type="number" label="Кол-во колёс" variant="bordered"
                                           value={numberOfWheels}
                                           onChange={(e) => setNumberOfWheels(e.target.value)} isRequired min={1}/>
                                </div>

                                <div className="grid grid-cols-2 gap-3">
                                    <Input type="number" label="Вместимость" variant="bordered" value={capacity}
                                           onChange={(e) => setCapacity(e.target.value)} min={0}/>
                                    <Input type="number" label="Пробег" variant="bordered" value={distanceTravelled}
                                           onChange={(e) => setDistanceTravelled(e.target.value)} min={0}/>
                                </div>

                                <div className="grid grid-cols-2 gap-3">
                                    <Input type="number" label="Расход топлива" variant="bordered"
                                           value={fuelConsumption}
                                           onChange={(e) => setFuelConsumption(e.target.value)}
                                           isRequired/>
                                    <Select label="Тип топлива" variant="bordered"
                                            selectedKeys={fuelType ? [fuelType] : []}
                                            onChange={(e) => setFuelType(e.target.value)} isRequired>
                                        {FUEL_TYPES.map((f) => (
                                            <SelectItem key={f} value={f}>{f}</SelectItem>
                                        ))}
                                    </Select>
                                </div>

                                {activeVehicle && (
                                    <div style={{marginTop: 12}}>
                                        <Button color="danger" variant="solid" onPress={handleDelete}>
                                            Удалить
                                        </Button>
                                    </div>
                                )}
                            </ModalBody>

                            <ModalFooter>
                                <Button variant="light" onPress={close}>Отмена</Button>
                                <Button color="primary" onPress={handleSave}>Сохранить</Button>
                            </ModalFooter>
                        </>
                    )}
                </ModalContent>
            </Modal>

            <Modal isOpen={isPresetOpen} onOpenChange={onPresetOpenChange} isDismissable>
                <ModalContent>
                    {(close) => (
                        <>
                            <ModalHeader>Пресеты</ModalHeader>
                            <ModalBody className="flex flex-col gap-5">
                                <div className="space-y-2">
                                    <div className="text-sm font-medium opacity-80">
                                        Вернуть один объект с минимальным distanceTravelled
                                    </div>
                                    <div className="flex items-center gap-3">
                                        <Button onPress={presetMinDistance}>Выполнить</Button>
                                    </div>
                                </div>

                                <div className="space-y-2">
                                    <div className="text-sm font-medium opacity-80">
                                        Вернуть количество объектов, у которых fuelConsumption &gt; X
                                    </div>
                                    <div className="flex items-center gap-3">
                                        <Input
                                            label="X (fuelConsumption)"
                                            type="number"
                                            value={presetFuelGtCount}
                                            onChange={(e) => setPresetFuelGtCount(e.target.value)}
                                        />
                                        <Button onPress={presetCountFuelGt}>Выполнить</Button>
                                    </div>
                                </div>

                                <div className="space-y-2">
                                    <div className="text-sm font-medium opacity-80">
                                        Вернуть массив объектов, у которых fuelConsumption &gt; X
                                    </div>
                                    <div className="flex items-center gap-3">
                                        <Input
                                            label="X (fuelConsumption)"
                                            type="number"
                                            value={presetFuelGtList}
                                            onChange={(e) => setPresetFuelGtList(e.target.value)}
                                        />
                                        <Button onPress={presetListFuelGt}>Выполнить</Button>
                                    </div>
                                </div>

                                <div className="space-y-2">
                                    <div className="text-sm font-medium opacity-80">
                                        Найти все транспортные средства заданного типа
                                    </div>
                                    <div className="flex items-center gap-3">
                                        <Select
                                            label="Тип ТС"
                                            selectedKeys={presetType ? [presetType] : []}
                                            onChange={(e) => setPresetType(e.target.value)}
                                            className="min-w-[180px]"
                                        >
                                            {VEHICLE_TYPES.map(t => <SelectItem key={t} value={t}>{t}</SelectItem>)}
                                        </Select>
                                        <Button onPress={presetListByType}>Выполнить</Button>
                                    </div>
                                </div>

                                <div className="space-y-2">
                                    <div className="text-sm font-medium opacity-80">
                                        Найти все ТС с мощностью двигателя в диапазоне [min, max]
                                    </div>
                                    <div className="grid grid-cols-3 gap-3 items-end">
                                        <Input label="Engine min" type="number" value={presetEngMin}
                                               onChange={(e) => setPresetEngMin(e.target.value)}/>
                                        <Input label="Engine max" type="number" value={presetEngMax}
                                               onChange={(e) => setPresetEngMax(e.target.value)}/>
                                        <Button onPress={presetListByEngineRange}>Выполнить</Button>
                                    </div>
                                </div>
                            </ModalBody>

                            <ModalFooter>
                                <Button variant="light" onPress={close}>Закрыть</Button>
                            </ModalFooter>
                        </>
                    )}
                </ModalContent>
            </Modal>

        </>
    );
}
