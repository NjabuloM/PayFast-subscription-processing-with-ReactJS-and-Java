import { configureStore } from "@reduxjs/toolkit";
import createSagaMiddleware from "redux-saga";

import reducers from "./reducers";
import sagas from "./sagas";

const configStore = (initialState = {}) => {
    const sagaMiddleware = createSagaMiddleware();
    
    const store = configureStore({
        reducer: reducers,
        middleware: (getDefaultMiddleware) => getDefaultMiddleware().concat(sagaMiddleware),
        preloadedState: initialState
    });

    sagaMiddleware.run(sagas);

    return store;
}

export default configStore;
