import React, {useEffect} from "react";
import {BrowserRouter as Router, Navigate, Route, Routes} from "react-router-dom";
import LoginPage from "./page/loginPage.jsx";
import MainPage from "./page/mainPage.jsx";
import NotFoundPage from "./page/notFoundPage.jsx";
import {toast} from "sonner";
import useAuthStore from "./store/auth.js";
import {API_BASE} from "../cfg.js";
import CoordinatesPage from "./page/coordinatesPage.jsx";

export default function App() {

    const {isAuthed, setIsAuthed} = useAuthStore();

    const PrivateRoute = ({children}) => {
        if (isAuthed === null) return null;
        return isAuthed ? children : <Navigate to="/login" replace/>;
    };

    const PublicRoute = ({children}) => {
        if (isAuthed === null) return null;
        return isAuthed ? <Navigate to="/" replace/> : children;
    };

    const checkSession = async () => {
        try {
            const res = await fetch(`${API_BASE}/api/auth/check-session`, {
                method: 'GET',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json',
                },
            });

            if (res.ok) {
                setIsAuthed(true);
            } else {
                switch (res.status) {
                    case 401:
                        setIsAuthed(false);
                        break;
                    default:
                        setIsAuthed(false);
                        toast.error(`Ошибка сервера: ${res.status} ${res.statusText}`);
                        break;
                }
            }
        } catch (err) {
            setIsAuthed(false);
            toast.error("Не удалось подключиться к серверу.");
        }
    };

    useEffect(() => {
        checkSession();
    }, [setIsAuthed]);

    return (
        <Router>
            <Routes>
                {/* PUBLIC */}
                <Route path="/login" element={
                    <PublicRoute>
                        <LoginPage/>
                    </PublicRoute>
                    // <LoginPage/>
                }/>

                {/* PRIVATE */}
                <Route path="/" element={
                    <PrivateRoute>
                        <MainPage/>
                    </PrivateRoute>
                    // <MainPage/>
                }/>

                <Route path="/coordinates" element={
                    <PrivateRoute>
                        <CoordinatesPage/>
                    </PrivateRoute>
                    // <CoordinatesPage/>
                }/>

                {/* 404 */}
                <Route path="*" element={<NotFoundPage/>}/>
            </Routes>
        </Router>
    );
}

