'use client';

import {useCallback, useEffect, useMemo, useRef, useState} from "react";
import {
    Button, Input, Modal, ModalBody, ModalContent, ModalFooter, ModalHeader,
    Popover, PopoverContent, PopoverTrigger, useDisclosure
} from "@heroui/react";
import {toast} from "sonner";
import styles from "./mainPage.module.css";
import {useNavigate} from "react-router-dom";
import useAuthStore from "../store/auth.js";
import {API_BASE} from "../../cfg.js";
import CoordinatesTable from "../component/coordinatesTable.jsx";
import CoordinatesPicker from "../component/CoordinatesPicker.jsx";

export default function CoordinatesPage() {
    const navigate = useNavigate();
    const {setIsAuthed} = useAuthStore();

    const [activeCoordinates, setActiveCoordinates] = useState(null);
    const [x, setX] = useState("");
    const [y, setY] = useState("");

    const [needReassign, setNeedReassign] = useState(false);
    const [refCount, setRefCount] = useState(0);
    const [reassignTo, setReassignTo] = useState(null); // {id, x, y}

    const {isOpen, onOpen, onOpenChange} = useDisclosure();

    const [tableControls, setTableControls] = useState(null);
    const [refreshGrid, setRefreshGrid] = useState(() => () => {});

    const wsRef = useRef(null);
    const reconnectTimerRef = useRef(null);
    const WS_URL = useMemo(() => {
        try {
            const u = new URL(API_BASE);
            const wsProto = u.protocol === "https:" ? "wss:" : "ws:";
            return `${wsProto}//${u.host}${u.pathname.replace(/\/+$/, '')}/ws/coordinates`;
        } catch {
            const loc = window.location;
            const wsProto = loc.protocol === "https:" ? "wss:" : "ws:";
            const base = API_BASE?.startsWith("/") ? API_BASE : `/${API_BASE || ""}`;
            return `${wsProto}//${loc.host}${base.replace(/\/+$/, '')}/ws/coordinates`;
        }
    }, []);

    const connectWs = useCallback(() => {
        if (wsRef.current?.readyState === WebSocket.OPEN ||
            wsRef.current?.readyState === WebSocket.CONNECTING) return;

        let retry = 1000;
        const openSocket = () => {
            const ws = new WebSocket(WS_URL);
            wsRef.current = ws;

            ws.onopen = () => { retry = 1000; };
            ws.onmessage = (evt) => {
                const msg = (evt.data || "").toString().trim();
                if (msg === "refresh") refreshGrid?.();
            };
            ws.onclose = () => {
                reconnectTimerRef.current = setTimeout(() => {
                    retry = Math.min(retry * 2, 10000);
                    openSocket();
                }, retry);
            };
            ws.onerror = () => { try { ws.close(); } catch {} };
        };

        openSocket();
    }, [WS_URL, refreshGrid]);

    useEffect(() => {
        connectWs();
        return () => {
            clearTimeout(reconnectTimerRef.current);
            try { wsRef.current?.close(); } catch {}
        };
    }, [connectWs]);

    const openNewCoordinatesModal = () => {
        setActiveCoordinates(null);
        setX("");
        setY("");
        setNeedReassign(false);
        setRefCount(0);
        setReassignTo(null);
        onOpen();
    };

    const openEditCoordinatesModal = async (coord) => {
        setActiveCoordinates(coord);
        setX(coord.x ?? "");
        setY(coord.y ?? "");
        setNeedReassign(false);
        setRefCount(0);
        setReassignTo(null);
        onOpen();
    };

    function validate() {
        if (x === "" || x === null) return "Заполните X.";
        if (y === "" || y === null) return "Заполните Y.";
        if (isNaN(Number(x))) return "X должен быть числом.";
        if (isNaN(Number(y))) return "Y должен быть числом.";
        return null;
    }

    const handleSave = async () => {
        const err = validate();
        if (err) return toast.warning(err);

        const isEdit = Boolean(activeCoordinates?.id);
        const url = isEdit
            ? `${API_BASE}/api/coordinates/${activeCoordinates.id}`
            : `${API_BASE}/api/coordinates`;

        const payload = isEdit
            ? { id: activeCoordinates.id, x: Number(x), y: Number(y) }
            : { x: Number(x), y: Number(y) };

        try {
            const res = await fetch(url, {
                method: isEdit ? "PUT" : "POST",
                credentials: "include",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(payload),
            });

            if (res.ok) {
                refreshGrid();
                onOpenChange(false);
                toast.success("Сохранено");
            } else {
                const data = await res.json().catch(() => ({}));
                toast.error(data.message || `Error: ${res.status}`);
            }
        } catch (e) {
            console.error(e);
            toast.error("Ошибка сохранения");
        }
    };

    const handleDelete = async () => {
        if (!activeCoordinates?.id) return;
        try {
            const res = await fetch(`${API_BASE}/api/coordinates/${activeCoordinates.id}`, {
                method: "DELETE",
                credentials: "include",
            });
            if (res.ok) {
                refreshGrid();
                onOpenChange(false);
                toast.success("Удалено");
                return;
            }
            const err = await res.json().catch(() => ({}));
            if (res.status === 409 && (err.code === "FK_CONSTRAINT" || /Нельзя удалить/i.test(err.message || ""))) {
                toast.error(err.message || "Нельзя удалить — есть связанные транспортные средства");
                setNeedReassign(true);
                setRefCount(err.refCount ?? 0);
                return;
            }
            toast.error(err.message || `Ошибка: ${res.status}`);
        } catch (e) {
            console.error(e);
            toast.error("Ошибка удаления");
        }
    };

    const handleConfirmReassignAndDelete = async () => {
        if (!activeCoordinates?.id) return;
        if (!reassignTo?.id) {
            toast.warning("Выберите целевые координаты");
            return;
        }
        if (reassignTo.id === activeCoordinates.id) {
            toast.warning("Нельзя переназначать на те же координаты");
            return;
        }

        try {
            const res = await fetch(
                `${API_BASE}/api/coordinates/${activeCoordinates.id}?reassignTo=${encodeURIComponent(reassignTo.id)}`,
                { method: "DELETE", credentials: "include" }
            );

            if (!res.ok) {
                let err = {};
                try { err = await res.json(); } catch {
                    try { err.message = await res.text(); } catch {}
                }
                toast.error(err?.message || `Ошибка: ${res.status}`);
                return;
            }

            toast.success(`Переназначено на (${reassignTo.x}; ${reassignTo.y}) и удалено`);
            setNeedReassign(false);
            setRefCount(0);
            setReassignTo(null);
            refreshGrid?.();
            onOpenChange(false);
        } catch (e) {
            console.error(e);
            toast.error("Не удалось переназначить и удалить");
        }
    };

    const handleLogout = async () => {
        try {
            const res = await fetch(`${API_BASE}/api/auth/logout`, {
                method: "POST",
                credentials: "include",
            });
            if (!res.ok) throw new Error(`${res.status} ${await res.text()}`);
            setIsAuthed(false);
            navigate("/login", {replace: true});
            toast.success("Вы вышли из аккаунта");
        } catch (err) {
            toast.error("Не удалось выйти: " + (err.message || ""));
        }
    };

    const handleResetFilters = () => {
        tableControls?.clearFilters();
    };

    return (
        <>
            <div className={styles.totalwrapp}>
                <div className={styles.top}>
                    <div className={styles.left}>
                        <h1 className={styles.title}>Справочник координат (Coordinates)</h1>
                        <div className={styles.btnWrapper}>
                            <Button color="primary" className={styles.control} onPress={openNewCoordinatesModal}>
                                Добавить
                            </Button>
                            <Button color="warning" className={styles.control} onPress={handleResetFilters}>
                                Сбросить фильтры
                            </Button>
                            <Button
                                color="primary"
                                className={styles.control}
                                onPress={() => navigate("/")}
                            >
                                Vehicle
                            </Button>
                        </div>
                    </div>
                    <div className={styles.right}>
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
                    </div>
                </div>
            </div>

            <div style={{width: "95%", margin: "24px auto"}}>
                <CoordinatesTable
                    onOpenEditCoordinatesModal={openEditCoordinatesModal}
                    onReadyRefresh={(fn) => setRefreshGrid(() => fn)}
                    onReadyControls={(controls) => setTableControls(controls)}
                />
            </div>

            <Modal isOpen={isOpen} onOpenChange={(v) => {
                onOpenChange(v);
                if (!v) {
                    setNeedReassign(false);
                    setRefCount(0);
                    setReassignTo(null);
                }
            }} isDismissable={false}>
                <ModalContent className={styles.postModalBody}>
                    {(close) => (
                        <>
                            <ModalHeader>
                                {activeCoordinates ? "Редактировать Coordinates" : "Новые Coordinates"}
                            </ModalHeader>

                            <ModalBody className={styles.postModalBody}>
                                <div className="grid grid-cols-2 gap-3">
                                    <Input
                                        label="X"
                                        variant="bordered"
                                        value={String(x)}
                                        onChange={(e) => setX(e.target.value)}
                                        isRequired
                                        type="number"
                                    />
                                    <Input
                                        label="Y"
                                        variant="bordered"
                                        value={String(y)}
                                        onChange={(e) => setY(e.target.value)}
                                        isRequired
                                        type="number"
                                    />
                                </div>

                                {activeCoordinates && !needReassign && (
                                    <div style={{marginTop: 12}}>
                                        <Button color="danger" variant="solid" onPress={handleDelete}>
                                            Удалить
                                        </Button>
                                    </div>
                                )}

                                {needReassign && (
                                    <div className="mt-4 space-y-3">
                                        <div className="text-sm opacity-80">
                                            Нельзя удалить координаты
                                            {activeCoordinates ? <> (<b>{activeCoordinates.x}</b>; <b>{activeCoordinates.y}</b>)</> : null}
                                            {typeof refCount === "number" ? <>: к ним привязано <b>{refCount}</b> ТС.</> : "."}
                                            <br/>
                                            Выберите другие координаты, на которые будут переназначены все ТС.
                                        </div>

                                        <CoordinatesPicker
                                            required
                                            excludeId={activeCoordinates?.id}
                                            value={reassignTo || { id: null, x: "", y: "" }}
                                            onChange={(sel) => setReassignTo(sel?.id ? sel : null)}
                                        />

                                        <div className="flex gap-3">
                                            <Button variant="light" onPress={() => {
                                                setNeedReassign(false);
                                                setRefCount(0);
                                                setReassignTo(null);
                                            }}>
                                                Отмена переназначения
                                            </Button>
                                            <Button color="primary" onPress={handleConfirmReassignAndDelete}>
                                                Переназначить и удалить
                                            </Button>
                                        </div>
                                    </div>
                                )}
                            </ModalBody>

                            <ModalFooter>
                                <Button variant="light" onPress={close}>Закрыть</Button>
                                {!needReassign && (
                                    <Button color="primary" onPress={handleSave}>Сохранить</Button>
                                )}
                            </ModalFooter>
                        </>
                    )}
                </ModalContent>
            </Modal>
        </>
    );
}
