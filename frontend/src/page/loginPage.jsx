'use client';

import {useState} from "react";
import {useLocation, useNavigate} from "react-router-dom";
import {Button, Input} from "@heroui/react";
import {toast} from "sonner";
import useAuthStore from "../store/auth.js";
import style from "./loginPage.module.css";
import {API_BASE} from "../../cfg.js";

export default function LoginPage() {
    const [login, setLogin] = useState("");
    const [password, setPassword] = useState("");

    const {setIsAuthed} = useAuthStore();
    const navigate = useNavigate();
    const from = useLocation().state?.from?.pathname || "/";

    const handleSubmit = async (e) => {
        e.preventDefault();

        try {
            const res = await fetch(`${API_BASE}/api/auth/login`, {
                method: "POST",
                credentials: "include",
                headers: {"Content-Type": "application/json"},
                body: JSON.stringify({login, password}),
            });

            if (res.ok) {
                setIsAuthed(true);
                navigate(from, {replace: true});
                toast.success("Успешный вход!");
                return;
            }

            if (res.status === 401) {
                toast.error("Неверный логин или пароль!");
            } else {
                toast.error(`Ошибка сервера: ${res.status} ${res.statusText}`);
            }
        } catch (err) {
            toast.error("Не удалось подключиться к серверу.");
        }
    };

    return (
        <div className={style.loginWrapper}>
            <form className={style.loginForm} onSubmit={handleSubmit}>
                <h1 className={style.loginTitle}>Войдите в свой аккаунт</h1>

                <Input
                    label="Логин"
                    value={login}
                    onChange={(e) => setLogin(e.target.value)}
                    required
                />

                <Input
                    label="Пароль"
                    type="password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    required
                />

                <Button type="submit" color="primary" fullWidth>
                    Войти
                </Button>
            </form>
        </div>
    );
}
