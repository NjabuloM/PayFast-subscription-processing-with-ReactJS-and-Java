import { all } from "redux-saga/effects";
import catalogueSaga from "./catalogue/saga";
import subscriptionSaga from "./subscription/saga";

export default function* rootSaga() {
    yield all([
        catalogueSaga(),
        subscriptionSaga()
    ]);
}