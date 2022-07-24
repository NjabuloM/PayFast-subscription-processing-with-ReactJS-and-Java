import React from "react";
import { Provider } from "react-redux";
import Application from "./containers/App";
import { BrowserRouter as Router, Routes, Route } from "react-router-dom";
import configStore from "./redux/store";
import OrderConfirmation from "./routes/checkout/order-confirm";
import PaymentSuccess from "./routes/payment/success";
import PaymentFailure from "./routes/payment/failure";

const FocalApp = () => (
    <Provider store={configStore()}>
        <Router>
            <Routes>
                <Route path='/' element={<Application/>} />
                <Route path='/confirm-order' element={<OrderConfirmation/>} />
                <Route path='/payment-success' element={<PaymentSuccess/>} />
                <Route path='/payment-failed' element={<PaymentFailure/>} />
            </Routes>
        </Router>
    </Provider>
);

export default FocalApp;