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
import PersonTable from "../component/personTable.jsx";
import OwnerPicker from "../component/OwnerPicker.jsx";

export default function PersonPage() {
    const navigate = useNavigate();
    const {setIsAuthed} = useAuthStore();

    const [activePerson, setActivePerson] = useState(null);
    const [fullName, setFullName] = useState("");

    // reassign-in-modal state
    const [needReassign, setNeedReassign] = useState(false);
    const [refCount, setRefCount] = useState(0);
    const [reassignTo, setReassignTo] = useState(null); // {id, fullName}

    const {isOpen, onOpen, onOpenChange} = useDisclosure();

    const [tableControls, setTableControls] = useState(null);
    const [refreshGrid, setRefreshGrid] = useState(() => () => {});

    // (опционально) WS
    const wsRef = useRef(null);
    const reconnectTimerRef = useRef(null);
    const WS_URL = useMemo(() => {
        try {
            const u = new URL(API_BASE);
            const wsProto = u.protocol === "https:" ? "wss:" : "ws:";
            return `${wsProto}//${u.host}${u.pathname.replace(/\/+$/, '')}/ws/persons`;
        } catch {
            const loc = window.location;
            const wsProto = loc.protocol === "https:" ? "wss:" : "ws:";
            const base = API_BASE?.startsWith("/") ? API_BASE : `/${API_BASE || ""}`;
            return `${wsProto}//${loc.host}${base.replace(/\/+$/, '')}/ws/persons`;
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

    const openNewPersonModal = () => {
        setActivePerson(null);
        setFullName("");
        // сбрасываем reassign-состояния
        setNeedReassign(false);
        setRefCount(0);
        setReassignTo(null);
        onOpen();
    };

    const openEditPersonModal = async (person) => {
        setActivePerson(person);
        setFullName(person.fullName || "");
        setNeedReassign(false);
        setRefCount(0);
        setReassignTo(null);
        onOpen();
    };

    function validate() {
        if (!fullName.trim()) return "Заполните fullName.";
        return null;
    }

    const handleSave = async () => {
        const err = validate();
        if (err) return toast.warning(err);

        const isEdit = Boolean(activePerson?.id);
        const url = isEdit
            ? `${API_BASE}/api/person/${activePerson.id}`
            : `${API_BASE}/api/person`;

        const payload = isEdit
            ? { id: activePerson.id, fullName: fullName.trim() }
            : { fullName: fullName.trim() };

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

    // Удаление с возможным «переназначением внутри этой же модалки»
    const handleDelete = async () => {
        if (!activePerson?.id) return;
        try {
            const res = await fetch(`${API_BASE}/api/person/${activePerson.id}`, {
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
            if (res.status === 409 && (err.code === "FK_CONSTRAINT" || /привязано/i.test(err.message || ""))) {
                toast.error(err.message || "Нельзя удалить — есть связанные транспортные средства");
                // показываем в ЭТОЙ модалке блок переназначения
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
        if (!activePerson?.id) return;
        if (!reassignTo?.id) {
            toast.warning("Выберите нового владельца");
            return;
        }
        // NEW: запрет «сам на себя» на клиенте
        if (reassignTo.id === activePerson.id) {
            toast.warning("Нельзя переназначать на самого себя");
            return;
        }

        try {
            const res = await fetch(
                `${API_BASE}/api/person/${activePerson.id}?reassignTo=${encodeURIComponent(reassignTo.id)}`,
                { method: "DELETE", credentials: "include" }
            );

            if (!res.ok) {
                // NEW: аккуратно читаем сначала JSON, потом текст
                let err = {};
                try { err = await res.json(); } catch {
                    try { err.message = await res.text(); } catch {}
                }
                toast.error(err?.message || `Ошибка: ${res.status}`);
                return;
            }

            toast.success(`Переназначено на «${reassignTo.fullName}» и удалено`);
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
                        <h1 className={styles.title}>Справочник людей (Person)</h1>
                        <div className={styles.btnWrapper}>
                            <Button color="primary" className={styles.control} onPress={openNewPersonModal}>
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
                <PersonTable
                    onOpenEditPersonModal={openEditPersonModal}
                    onReadyRefresh={(fn) => setRefreshGrid(() => fn)}
                    onReadyControls={(controls) => setTableControls(controls)}
                />
            </div>

            <Modal isOpen={isOpen} onOpenChange={(v) => {
                onOpenChange(v);
                if (!v) {
                    // сбрасываем «режим переназначения» при закрытии
                    setNeedReassign(false);
                    setRefCount(0);
                    setReassignTo(null);
                }
            }} isDismissable={false}>
                <ModalContent className={styles.postModalBody}>
                    {(close) => (
                        <>
                            <ModalHeader>
                                {activePerson ? "Редактировать Person" : "Новый Person"}
                            </ModalHeader>

                            <ModalBody className={styles.postModalBody}>
                                <Input
                                    label="Full Name"
                                    variant="bordered"
                                    value={fullName}
                                    onChange={(e) => setFullName(e.target.value)}
                                    isRequired
                                />

                                {activePerson && !needReassign && (
                                    <div style={{marginTop: 12}}>
                                        <Button color="danger" variant="solid" onPress={handleDelete}>
                                            Удалить
                                        </Button>
                                    </div>
                                )}

                                {/* Блок «переназначить» появляется после 409 */}
                                {needReassign && (
                                    <div className="mt-4 space-y-3">
                                        <div className="text-sm opacity-80">
                                            Нельзя удалить владельца
                                            {activePerson?.fullName ? <> «<b>{activePerson.fullName}</b>»</> : null}
                                            {typeof refCount === "number" ? <>: к нему привязано <b>{refCount}</b> ТС.</> : "."}
                                            <br/>
                                            Выберите нового владельца, на которого будут переназначены все ТС.
                                        </div>

                                        <OwnerPicker
                                            required
                                            excludeId={activePerson?.id}
                                            value={reassignTo || { id: null, fullName: "" }}
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
