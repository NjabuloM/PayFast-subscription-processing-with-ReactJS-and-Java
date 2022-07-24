import { call, put, takeEvery, all, fork } from "redux-saga/effects";
import { getMagazineCatalogue } from "./api";
import { browseNewsStand, showMagazineStand } from "./reducer";


function* requestCatalogueSaga() {
    const newsstand = yield call(getMagazineCatalogue);
    yield put(showMagazineStand(newsstand));
}

export function* watchCatalogue() {
    yield takeEvery(browseNewsStand, requestCatalogueSaga);
}

export default function* rootSaga() {
    yield all([
        fork(watchCatalogue)
    ]);
}