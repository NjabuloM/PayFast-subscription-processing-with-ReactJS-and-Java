import { all, call, fork, put, takeEvery } from "redux-saga/effects";
import { selectSubscription, payForSubscription, receiveFormData, viewMagazineDetails, receiveMagazineDetails } from "./reducer";
import { addSubscription, checkOut } from "./api";
import { getMagazine, getPlans } from "../catalogue/api";

function* subscriptionSelectionSaga({ payload }) {
    const {selectedMagazineId} = payload;
    yield call(addSubscription, selectedMagazineId);
}

function* subscriptionPaymentSaga({ payload }) {
    const subscriptionInfo = payload;
    try {
        const formData = yield call(checkOut, subscriptionInfo);
        yield put(receiveFormData({formData}));
    } catch(error) {
        console.log("Checkout failed", error);
        yield put(receiveFormData({formData: {error: (error && error.Error) || 'Checkout failed. Please try again.'}}));
    }
}

function* requestMagazineSaga() {
    const selectedMagazineId = localStorage.getItem("selected_magazine_id");
    try {
        const [magazine, plans] = yield all([
            call(getMagazine, selectedMagazineId),
            call(getPlans, selectedMagazineId)
        ]);
        yield put(receiveMagazineDetails({magazine, plans}));
    } catch(error) {
        console.log("Could not load the magazine", error);
        yield put(receiveMagazineDetails({magazine: {}, plans: []}));
    }
}

export function* watchSubscriptionSelection() {
    yield takeEvery(selectSubscription, subscriptionSelectionSaga);
}

export function* watchSubscriptionPayment() {
    yield takeEvery(payForSubscription, subscriptionPaymentSaga);
}

export function* watchMagazine() {
    yield takeEvery(viewMagazineDetails, requestMagazineSaga)
}

export default function* rootSaga() {
    yield all([
        fork(watchSubscriptionSelection),
        fork(watchSubscriptionPayment),
        fork(watchMagazine)
    ]);
}
