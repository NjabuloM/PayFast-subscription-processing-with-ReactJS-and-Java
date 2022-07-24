import { combineReducers } from "@reduxjs/toolkit";
import catalogue from "./catalogue/reducer";
import subscribe from "./subscription/reducer";

const reducers = combineReducers({
    catalogue,
    subscribe
});

export default reducers;